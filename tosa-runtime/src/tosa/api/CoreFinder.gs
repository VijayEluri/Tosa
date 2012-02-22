package tosa.api

uses tosa.loader.IDBType
uses java.lang.IllegalArgumentException
uses java.util.Map
uses tosa.impl.query.SqlStringSubstituter
uses gw.lang.reflect.features.PropertyReference
uses tosa.loader.Util
uses java.util.Arrays
uses java.lang.IllegalStateException
uses tosa.impl.QueryResultImpl
uses java.sql.ResultSet
uses tosa.impl.CachedDBObject
uses tosa.impl.QueryExecutor
uses tosa.impl.QueryExecutorImpl

/**
 */
class CoreFinder<T extends IDBObject> {

  var _dbType : IDBType
  var _queryExecutor : QueryExecutor

  construct(dbType : IDBType) {
    this(dbType, new QueryExecutorImpl(dbType.Table.Database))
  }

  construct(dbType : IDBType, queryExecutor : QueryExecutor) {
    _dbType = dbType
    _queryExecutor = queryExecutor
  }


  /**
   * Loads the entity with the given id.  Returns null if no entity exists with that id.
   *
   * @param dbType the implicit dbType initial argument
   * @param id the id to load
   */
  function fromId(id : long) : T {
    var table = _dbType.Table
    // TODO - AHK - Should this be a constant?  Or a getIdColumn method since I call it so often?
    var idColumn = table.getColumn("id")
    var query = sub("SELECT * FROM :table WHERE :id_column = :id",
                    {"table" -> table, "id_column" -> idColumn, "id" -> id})

    var results = selectEntity(_dbType.Name + ".fromId()", query.Sql, query.Params)

    if (results.Count == 0) {
      return null
    } else if (results.Count == 1) {
      return results.get(0) as T
    } else {
      throw "More than one row in table ${table.Name} had id ${id}";
    }
  }

  function count(sql : String, params : Map<String, Object> = null) : long {
    // TODO - AHK - Is this the right thing to enforce?  Should we enforce SELECT count(*) as count FROM <table>?
    // Should we even bother enforcing it here, or let it be enforced in countImpl()?
    if (!sql.startsWith("SELECT count(*) as count")) {
      throw new IllegalArgumentException("The count(String, Map) method must always be called with 'SELECT count(*) as count FROM' as the start of the statement.  The sql passed in was " + sql)
    }

    var countArgs = subIfNecessary(sql, null, params)
    return _queryExecutor.count(_dbType.Name + ".count(String, Map)", countArgs.Sql, countArgs.Params)
  }

  function countWhere(sql : String, params : Map<String, Object> = null) : long {
    // TODO - AHK - Is this the right thing to enforce?
    if (sql != null && sql.toUpperCase().startsWith("SELECT")) {
      throw new IllegalArgumentException("The countWhere(String, Map) method should only be called with the WHERE clause of a query.  To specify the full SQL for the query, use the count(String, Map) method instead.")
    }

    var queryPrefix = sub("SELECT count(*) as count FROM :table", {"table" -> _dbType.Table}).Sql
    var countArgs = subIfNecessary(sql, queryPrefix, params)
    return _queryExecutor.count(_dbType.Name + ".countWhere(String, Map)", countArgs.Sql, countArgs.Params)
  }

  function countAll() : long {
    var sql = sub("SELECT count(*) as count FROM :table", {"table" -> _dbType.Table}).Sql
    return _queryExecutor.count(_dbType.Name + ".countAll()", sql, {});
  }

  function countLike(template: T) : long {
    var queryPrefix = sub("SELECT count(*) as count FROM :table", {"table" -> _dbType.Table}).Sql
    var whereClause = buildWhereClause(template)
    return _queryExecutor.count(_dbType.Name + ".countLike(" + _dbType.Name + ")", queryPrefix + whereClause.Sql, whereClause.Params)
  }

  function select(sql : String, params : Map<String, Object> = null) : QueryResult<T> {
    // TODO - AHK - Is this the right thing to enforce?  Should we enforce SELECT * FROM <table>?
    // Should we even bother enforcing it here, or let it be enforced in selectEntity?
    if (!sql.toUpperCase().startsWith("SELECT * FROM")) {
      throw new IllegalArgumentException("The select(String, Map) method must always be called with 'SELECT * FROM' as the start of the statement.  The sql passed in was " + sql)
    }

    var selectArgs = subIfNecessary(sql, null, params)
    return selectEntity(_dbType.Name + ".select(String, Map)", selectArgs.Sql, selectArgs.Params) as QueryResult<T>
  }

  function selectWhere(sql : String, params : Map<String, Object> = null) : QueryResult<T> {
    // TODO - AHK - Is this the right thing to enforce?
    if (sql != null && sql.toUpperCase().startsWith("SELECT")) {
      throw new IllegalArgumentException("The selectWhere(String, Map) method should only be called with the WHERE clause of a query.  To specify the full SQL for the query, use the select(String, Map) method instead.")
    }

    var queryPrefix = sub("SELECT * FROM :table", {"table" -> _dbType.Table}).Sql
    var selectArgs = subIfNecessary(sql, queryPrefix, params)
    return selectEntity(_dbType.Name + ".selectWhere(String, Map)", selectArgs.Sql, selectArgs.Params) as QueryResult<T>
  }

  function selectLike(template : T) : QueryResult<T> {
    var queryPrefix = sub("SELECT * FROM :table", {"table" -> _dbType.Table}).Sql
    var whereClause = buildWhereClause(template)
    return selectEntity(_dbType.Name + ".selectWhere(String, Map)", queryPrefix + whereClause.Sql, whereClause.Params) as QueryResult<T>
  }

  function selectAll() : QueryResult<T> {
    var sql = sub("SELECT * FROM :table", {"table" -> _dbType.Table}).Sql
    return selectEntity(_dbType.Name + ".selectAll()", sql, {}) as QueryResult<T>
  }

  // ---------------------- Private Helper Methods ------------------------------------

  private static function subIfNecessary(sql : String, prefix : String, params : Map<String, Object>) : SqlStringSubstituter.SqlAndParams {
    var queryString : String
    var paramArray : Object[]
    if (sql == null || sql.Empty) {
      queryString = prefix
      paramArray = {}
    } else if (params != null) {
      var query = sub(sql, params)
      queryString = (prefix == null ? query.Sql : prefix + " WHERE " + query.Sql)
      paramArray = query.Params
    } else {
      queryString = (prefix == null ? sql : prefix + " WHERE " + sql)
      paramArray = {}
    }
    return new SqlStringSubstituter.SqlAndParams(queryString, paramArray)
  }

  private static function sub(input : String, tokenValues : Map<String, Object>) : SqlStringSubstituter.SqlAndParams {
    return SqlStringSubstituter.substitute(input, tokenValues)
  }

  private static function buildWhereClause(template : IDBObject) : SqlStringSubstituter.SqlAndParams {
    var clauses : List<String> = {}
    var params : List<Object> = {}
    if (template != null) {
      for (column in template.DBTable.Columns) {
        var value = template.getColumnValue(column.Name)
        if (value != null) {
          var result = sub(":column = :value", {"column" -> column, "value" -> value})
          clauses.add(result.Sql)
          params.add(result.ParamObjects[0])
        }
      }
    }

    if (not clauses.Empty) {
      return new SqlStringSubstituter.SqlAndParams(" WHERE " + clauses.join(" AND "), params.toTypedArray())
    } else {
      // TODO - AHK - Should we just leave the clause out entirely?
      return new SqlStringSubstituter.SqlAndParams(" WHERE 1 = 1", params.toTypedArray())
    }
  }

  private function selectEntity(profilerTag : String, sqlStatement : String, parameters : IPreparedStatementParameter[]) : QueryResult<IDBObject> {
    // TODO - AHK - Validate the input string here?
    return new QueryResultImpl<IDBObject>(
        sqlStatement,
        parameters,
        \s, p -> _queryExecutor.selectEntity(profilerTag, _dbType, s, p));
  }
}