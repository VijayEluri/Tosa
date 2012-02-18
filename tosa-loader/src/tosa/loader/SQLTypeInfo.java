package tosa.loader;

import gw.lang.GosuShop;
import gw.lang.parser.ISymbol;
import gw.lang.reflect.*;
import gw.lang.reflect.java.IJavaType;
import gw.lang.reflect.java.JavaTypes;
import gw.util.GosuExceptionUtil;
import gw.util.concurrent.LockingLazyVar;
import org.slf4j.profiler.Profiler;
import tosa.api.IPreparedStatementParameter;
import tosa.api.IQueryResultProcessor;
import tosa.db.execution.QueryExecutor;
import tosa.dbmd.DatabaseImpl;
import tosa.dbmd.PreparedStatementParameterImpl;
import tosa.loader.data.DBData;
import tosa.loader.parser.SQLParseException;
import tosa.loader.parser.tree.*;

import java.sql.*;
import java.util.*;

public class SQLTypeInfo extends BaseTypeInfo {

  private List<IMethodInfo> _methods;
  private ISQLType _sqlType;
  private SQLParseException _sqlpe;
  private IMethodInfo _selectMethod;
  private LockingLazyVar<List<SQLParameterInfo>> _parameters = new LockingLazyVar<List<SQLParameterInfo>>(){
    @Override
    protected List<SQLParameterInfo> init() {
      List<VariableExpression> vars = _sqlType.getData().getVariables();
      return determineParameters(_sqlType.getData().getDBData(), vars);
    }
  };

  public SQLTypeInfo(ISQLType sqlType) {
    super(sqlType);
    _sqlType = sqlType;
    _methods = new ArrayList<IMethodInfo>();
    ParameterInfoBuilder[] queryParameters = determineParameters();
    _sqlpe = _sqlType.getData().getSelect().getSQLParseException(_sqlType.getData().getFileName());

    final IType selectReturnType = getResultsType();

    _selectMethod = new MethodInfoBuilder().withName("select")
      .withStatic()
      .withParameters(queryParameters)
      .withReturnType(JavaTypes.ITERABLE().getParameterizedType(selectReturnType))
      .withCallHandler(new IMethodCallHandler() {
        @Override
        public Object handleCall(Object ctx, Object... args) {
          return invokeQuery(selectReturnType, args);
        }
      }).build(this);
    _methods.add(_selectMethod);
  }

  public Object invokeQuery(IType returnType, Object... args) {
    verifySql();
    HashMap<String, Object> values = makeArgMap(args);

    DatabaseImpl database = _sqlType.getData().getDatabase();
    String sql = _sqlType.getData().getSQL(values);
    List<VariableExpression> vars = _sqlType.getData().getVariables();
    List<IPreparedStatementParameter> params = new ArrayList<IPreparedStatementParameter>();

    for (VariableExpression var : vars) {
      if (var.shouldApply(values)) {
        Object value = values.get(var.getName());
        if (var.isList()) {
          if (value != null) {
            List valueList = (List) value;
            for (Object listValue : valueList) {
              params.add(new PreparedStatementParameterImpl(listValue, PreparedStatementParameterImpl.UNKNOWN));
            }
          }
        } else {
          params.add(new PreparedStatementParameterImpl(value, PreparedStatementParameterImpl.UNKNOWN));
        }
      }
    }

    Profiler profiler = Util.newProfiler("");
    profiler.start(_sqlType.getName() + ".select()");
    try {
      return database.getDBExecutionKernel().executeSelect(sql, new SQLTypeInfoQueryProcessor(returnType), params.toArray(new IPreparedStatementParameter[params.size()]));
    } finally {
      profiler.stop();
    }
  }

  private void verifySql() {
    if (_sqlpe != null) {
      throw _sqlpe;
    }
  }

  private HashMap<String, Object> makeArgMap(Object[] args) {
    List<SQLParameterInfo> pis = getParameters();
    HashMap<String, Object> values = new HashMap<String, Object>();
    for (int i = 0; i < pis.size(); i++) {
      SQLParameterInfo pi = pis.get(i);
      values.put(pi.getName(), args[i]);
    }
    return values;
  }

  private List<SQLParameterInfo> getParameters() {
    return _parameters.get();
  }

  private List<SQLParameterInfo> determineParameters(DBData dbData, List<VariableExpression> vars) {
    Map<String, SQLParameterInfo> pis = new LinkedHashMap<String, SQLParameterInfo>();
    for (VariableExpression var : vars) {
      SQLParameterInfo pi = pis.get(var.getName());
      if (pi == null) {
        pi = new SQLParameterInfo(var.getName());
        pis.put(var.getName(), pi);
      }
      pi.addVariableExpression(var);
    }
    return new ArrayList<SQLParameterInfo>(pis.values());
  }

  private IType getResultsType() {
    SelectStatement select = _sqlType.getData().getSelect();
    if (select.hasSingleTableTarget()) {
      IType type = TypeSystem.getByFullNameIfValid(_sqlType.getData().getDatabase().getNamespace() + "." + select.getPrimaryTableName());
      if (type != null) {
        return type;
      }
    } else if(select.hasSpecificColumns()) {
      return getStructResultType();
    }
    return getMapType();
  }

  private IType getStructResultType() {
    //TODO cgross - register this type and make it a type ref
    return new StructType(_sqlType.getTypeLoader(), _sqlType.getName() + "Result", _sqlType.getData().getSelect().getColumnMap());
  }

  private IType getMapType() {
    return JavaTypes.MAP().getGenericType().getParameterizedType(JavaTypes.STRING(), JavaTypes.OBJECT());
  }

  private Object constructResultElement(ResultSet resultSet, IType returnType) {
    try {
      if (getMapType().equals(returnType)) {
        int count = resultSet.getMetaData().getColumnCount();
        Map<String, Object> hashMap = new HashMap<String, Object>();
        while (count > 0) {
          hashMap.put(resultSet.getMetaData().getColumnName(count), resultSet.getObject(count));
          count--;
        }
        return hashMap;
      } else if (returnType instanceof StructType) {
        Map<String, IType> propMap = ((StructType) returnType).getPropMap();
        Map vals = new HashMap();
        for (String name : propMap.keySet()) {
          Object val = resultSet.getObject(resultSet.findColumn(name));
          vals.put(name, val);
        }
        return ((StructType) returnType).newInstance(vals);
      } else if (returnType instanceof IDBType) {
        return QueryExecutor.buildObject((IDBType) returnType, resultSet);
      } else {
        throw new IllegalStateException("Do not know how to construct objects of type " + returnType.getName());
      }
    } catch (SQLException e) {
      throw GosuExceptionUtil.forceThrow(e);
    }
  }

  private ParameterInfoBuilder[] determineParameters() {
    ArrayList<ParameterInfoBuilder> builders = new ArrayList<ParameterInfoBuilder>();
    List<SQLParameterInfo> pis = getParameters();
    for (SQLParameterInfo pi : pis) {
      builders.add(new ParameterInfoBuilder().withName(pi.getName().substring(1)).withType(pi.getGosuType()).withDefValue(GosuShop.getNullExpressionInstance()));
    }
    return builders.toArray(new ParameterInfoBuilder[0]);
  }

  @Override
  public List<? extends IMethodInfo> getMethods() {
    return _methods;
  }

  @Override
  public IMethodInfo getMethod(CharSequence methodName, IType... params) {
    return ITypeInfo.FIND.callableMethod(getMethods(), methodName, params);
  }

  @Override
  public IMethodInfo getCallableMethod(CharSequence method, IType... params) {
    return getMethod(method, params);
  }

  private class SQLTypeInfoQueryProcessor implements IQueryResultProcessor<Object> {
    private IType _returnType;

    public SQLTypeInfoQueryProcessor(IType type) {
      _returnType = type;
    }

    @Override
    public Object processResult(ResultSet result) throws SQLException {
      return constructResultElement(result, _returnType);
    }
  }
}
