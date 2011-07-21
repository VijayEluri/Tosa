package tosa.loader;

import gw.config.CommonServices;
import gw.lang.reflect.*;
import gw.lang.reflect.features.PropertyReference;
import gw.lang.reflect.java.IJavaType;
import gw.util.concurrent.LazyVar;
import tosa.CachedDBObject;
import tosa.api.*;
import tosa.api.query.CoreFinder;
import tosa.dbmd.DBColumnImpl;
import tosa.impl.query.CoreFinderImpl;

import java.nio.channels.WritableByteChannel;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/29/10
 * Time: 10:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class DBTypeInfo extends TosaBaseTypeInfo implements ITypeInfo {

  public static final String ID_COLUMN = "id";

  private CoreFinder<IDBObject> _finder;

  public DBTypeInfo(IDBType dbType) {
    super(dbType);
    // TODO - AHK - Type reference?
    _finder = new CoreFinderImpl<IDBObject>(dbType);

    createMethod("fromId", params(param(ID_COLUMN, IJavaType.pLONG, "The id to load")), dbType, Modifiers.PublicStatic,
        "Loads the entity with the given id.  Returns null if no entity exists with that id.",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.fromId((Long) args[0]);
          }
        });

    createMethod("countWithSql", params(param("sql", IJavaType.STRING, null)), IJavaType.pINT, Modifiers.PublicStatic, "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.countWithSql((String) args[0]);
          }
        });

    createMethod("count", params(param("template", dbType, null)), IJavaType.pINT, Modifiers.PublicStatic, "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.count((IDBObject) args[0]);
          }
        });

    createMethod("findWithSql", params(param("sql", IJavaType.STRING, null)), IJavaType.LIST.getGenericType().getParameterizedType(dbType), Modifiers.PublicStatic, "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.findWithSql((String) args[0]);
          }
        });

    createMethod("find", params(param("template", dbType, null)), IJavaType.LIST.getGenericType().getParameterizedType(dbType), Modifiers.PublicStatic, "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.find((IDBObject) args[0]);
          }
        });

    createMethod("findSorted",
        params(param("template", dbType, null),
            param("sortProperty", TypeSystem.get(PropertyReference.class).getParameterizedType(dbType, IJavaType.OBJECT), null),
            param("ascending", IJavaType.pBOOLEAN, null)),
        IJavaType.LIST.getGenericType().getParameterizedType(dbType),
        Modifiers.PublicStatic,
        "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.findSorted((IDBObject) args[0], (PropertyReference) args[1], (Boolean) args[2]);
          }
        });

    createMethod("findPaged",
        params(param("template", dbType, null),
            param("pageSize", IJavaType.pINT, null),
            param("offset", IJavaType.pINT, null)),
        IJavaType.LIST.getGenericType().getParameterizedType(dbType),
        Modifiers.PublicStatic,
        "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.findPaged((IDBObject) args[0], (Integer) args[1], (Integer) args[2]);
          }
        }
    );

    createMethod("findSortedPaged",
        params(param("template", dbType, null),
            param("sortProperty", TypeSystem.get(PropertyReference.class).getParameterizedType(dbType, IJavaType.OBJECT), null),
            param("ascending", IJavaType.pBOOLEAN, null),
            param("pageSize", IJavaType.pINT, null),
            param("offset", IJavaType.pINT, null)),
        IJavaType.LIST.getGenericType().getParameterizedType(dbType),
        Modifiers.PublicStatic,
        "",
        new IMethodCallHandler() {
          @Override
          public Object handleCall(Object ctx, Object... args) {
            return _finder.findSortedPaged((IDBObject) args[0], (PropertyReference) args[1], (Boolean) args[2], (Integer) args[3], (Integer) args[4]);
          }
        }
    );

    for (IDBColumn column : dbType.getTable().getColumns()) {
      // TODO - AHK - Ideally this cast wouldn't be necessary
      addProperty(makeProperty((DBColumnImpl) column));
    }

    for (IDBArray dbArray : getDBType().getTable().getArrays()) {
      addProperty(makeArrayProperty(dbArray));
    }

    addConstructor(new ConstructorInfoBuilder()
        .withConstructorHandler(new IConstructorHandler() {
          @Override
          public Object newInstance(Object... args) {
            return new CachedDBObject(getDBType(), true);
          }
        }).build(this));

    lockDataStructures();
  }

  private DBPropertyInfo makeProperty(DBColumnImpl column) {
    return new DBPropertyInfo(this, column);
  }

  private IPropertyInfo makeArrayProperty(IDBArray dbArray) {
    return new DBArrayPropertyInfo(this, dbArray);
  }

  private IDBType getDBType() {
    return ((IDBType) getOwnersType());
  }
}
