package tosa.loader

uses tosa.loader.IDBType
uses tosa.api.IDBObject
uses gw.lang.reflect.features.PropertyReference
uses tosa.impl.util.StringSubstituter
uses tosa.impl.query.SqlStringSubstituter
uses java.util.Map
uses gw.util.Pair


/**
 * This class effectively serves as a static mix-in for the DBType types.  Each function in here
 * needs to have its first argument be an IDBType object.  In addition, parameters that are
 * typed as IDBObject will be transformed at delegation time to the type in question, i.e.
 * something like tosa.testdb.Foo will be used in place of IDBObject, providing a sort of
 * covariance on the delegated type.  Any getter/setter pairs here will be turned into
 * properties for delegation purposes.  Every function on this class needs to be static.
 *
 * These methods SHOULD NOT be called directly by code:  they're purely here for delegation
 * purposes, to make it easier (and clearer) to write the static methods that are automatically
 * added to every DBType.
 */
class DBTypeDelegate {

  /**
   * Loads the entity with the given id.  Returns null if no entity exists with that id.
   *
   * @param dbType the implicit dbType initial argument
   * @param id the id to load
   */
  static function fromId(dbType : IDBType, id : long) : IDBObject {
    var table = dbType.Table
    // TODO - AHK - Should this be a constant?  Or a getIdColumn method since I call it so often?
    var idColumn = table.getColumn("id")
    var query = sub("SELECT * FROM :table WHERE :id_column = :id",
                    {"table" -> table, "id_column" -> idColumn, "id" -> id})

    var results = dbType.NewQueryExecutor.selectEntity(dbType.Name + ".fromId()", dbType, query.First, query.Second)

    if (results.Count == 0) {
      return null
    } else if (results.Count == 1) {
      return results.get(0)
    } else {
      throw "More than one row in table ${table.Name} had id ${id}";
    }
  }

  private static function sub(input : String, tokenValues : Map<String, Object>) : Pair<String, Object[]> {
    return SqlStringSubstituter.substitute(input, tokenValues)
  }

  /**
   * Executes a count query in the database.
   *
   * @param sql the sql to execute
   */
  static function countWithSql(dbType : IDBType, sql : String) : int {
    return dbType.Finder.countWithSql(sql)
  }

  /**
   * Executes a count query in the database using the given object as a template.
   *
   * @param template the template object to form the query from
   */
  static function count(dbType : IDBType, template : IDBObject) : int {
    return dbType.Finder.count(template)
  }

  static function findWithSql(dbType : IDBType, sql : String) : List<IDBObject> {
    return dbType.Finder.findWithSql(sql)
  }

  static function find(dbType : IDBType, template : IDBObject) : List<IDBObject> {
    return dbType.Finder.find(template)
  }

  static function findSorted(dbType : IDBType, template : IDBObject, sortProperty : PropertyReference<IDBObject, Object>, ascending : boolean) : List<IDBObject> {
    return dbType.Finder.findSorted(template, sortProperty, ascending)
  }

  static function findPaged(dbType : IDBType, template : IDBObject, pageSize : int, offset : int) : List<IDBObject> {
    return dbType.Finder.findPaged(template, pageSize, offset)
  }

  static function findSortedPaged(dbType : IDBType, template : IDBObject, sortProperty : PropertyReference<IDBObject, Object>, ascending : boolean, pageSize : int, offset : int) : List<IDBObject> {
    return dbType.Finder.findSortedPaged(template, sortProperty, ascending, pageSize, offset)
  }
}