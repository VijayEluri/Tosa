package tosa.loader.parser.tree;

import gw.lang.reflect.IType;
import gw.lang.reflect.java.IJavaType;
import gw.lang.reflect.java.JavaTypes;
import tosa.api.IDBColumnType;
import tosa.loader.SQLParameterInfo;
import tosa.loader.data.ColumnData;
import tosa.loader.data.DBData;
import tosa.loader.data.TableData;
import tosa.loader.parser.Token;

import java.util.*;

public class SelectStatement extends SQLParsedElement implements IRootParseElement {

  private SQLParsedElement _quantifier;
  private SQLParsedElement _selectList;
  private TableExpression _tableExpr;
  private SQLParsedElement _orderBy;
  private List<VariableExpression> _variables;
  private DBData _dbData;

  public SelectStatement(Token start, SQLParsedElement quantifier, SQLParsedElement selectList, TableExpression tableExpr, SQLParsedElement orderBy) {
    super(start, quantifier, selectList, tableExpr, orderBy);
    _quantifier = quantifier;
    _selectList = selectList;
    _tableExpr = tableExpr;
    _orderBy = orderBy;
  }

  @Override
  protected void toSQL(boolean prettyPrint, int indent, StringBuilder sb, Map<String, Object> values) {
    pp(prettyPrint, indent, "SELECT ", sb);
    if (_quantifier != null) {
      _quantifier.toSQL(prettyPrint, indent, sb, values);
    }
    _selectList.toSQL(prettyPrint, indent, sb, values);
    sb.append("\n");
    _tableExpr.toSQL(prettyPrint, indent, sb, values);
    if (_orderBy != null) {
      sb.append("\n");
      _orderBy.toSQL(prettyPrint, indent, sb, values);
    }
  }

  public void verify(DBData dbData) {
    super.verify(dbData);
    _variables = determineVariables(dbData);
  }

  private List<VariableExpression> determineVariables(DBData dbData) {
    List<VariableExpression> vars = findDescendents(VariableExpression.class);
    Collections.sort(vars, SQLParsedElement.OFFSET_COMPARATOR);
    return vars;
  }

  @Override
  public String getPrimaryTableName() {
    return getPrimaryTable() == null ? null : getPrimaryTable().getName().getValue();
  }

  public List<VariableExpression> getVariables() {
    return _variables;
  }

  public boolean hasSingleTableTarget() {
    return (_selectList instanceof AsteriskSelectList && getPrimaryTable() != null) ||
           _selectList instanceof ColumnSelectList && ((ColumnSelectList)_selectList).hasSingleTableTarget();
  }

  public boolean hasMultipleTableTargets() {
    return _selectList instanceof AsteriskSelectList &&
      (_tableExpr.getFrom().getTableRefs().size() > 1 ||
        !(_tableExpr.getFrom().getTableRefs().get(0) instanceof SimpleTableReference));
  }

  public boolean hasSpecificColumns() {
    return _selectList instanceof ColumnSelectList && !((ColumnSelectList) _selectList).hasSingleTableTarget();
  }

  private SimpleTableReference getPrimaryTable() {
    if (_selectList instanceof ColumnSelectList && ((ColumnSelectList) _selectList).hasSingleTableTarget()) {
      String tableName = ((ColumnSelectList) _selectList).getSingleTableTargetName();
      for (SimpleTableReference reference : getOrderedSimpleTableRefs()) {
        if (reference.getName().match(tableName)) {
          return reference;
        }
      }
    } else {
      List<SimpleTableReference> simpleRefs = getOrderedSimpleTableRefs();
      if (simpleRefs.size() > 0) {
        return simpleRefs.get(0);
      }
    }
    return null;
  }

  private List<SimpleTableReference> getOrderedSimpleTableRefs() {
    List<SimpleTableReference> simpleRefs = _tableExpr.getFrom().findDescendents(SimpleTableReference.class);
    Collections.sort(simpleRefs, SQLParsedElement.OFFSET_COMPARATOR);
    return simpleRefs;
  }

  public Map<String,IType> getColumnMap() {
    HashMap<String, IType> cols = new HashMap<String, IType>();
    if (hasSpecificColumns()) {
      for (SQLParsedElement col : _selectList.getChildren()) {
        IDBColumnType type = col.getDBType();
        IType gosuType = type == null ? JavaTypes.OBJECT() : type.getGosuType();
        if (col instanceof ColumnReference) {
          cols.put(((ColumnReference) col).getName(), gosuType);
        } else if (col instanceof DerivedColumn) {
          cols.put(((DerivedColumn) col).getName(), gosuType);
        }
      }
    } else if(hasMultipleTableTargets()) {
      List<SimpleTableReference> trs = getOrderedSimpleTableRefs();
      for (SimpleTableReference ref : trs) {
        TableData tableData = ref.getTableData();
        List<ColumnData> columns = tableData.getColumns();
        for (ColumnData column : columns) {
          if (!cols.containsKey(column.getName())) {
            cols.put(column.getName(), column.getColumnType().getGosuType());
          }
        }
      }
    }
    return cols;
  }

  public void setDBData(DBData data) {
    _dbData = data;
  }

  public DBData getDBData() {
    return _dbData;
  }
}
