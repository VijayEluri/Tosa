package tosa.loader.data;

import gw.fs.IFile;
import gw.util.concurrent.LockingLazyVar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/26/10
 * Time: 11:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class DBData {
  // TODO - AHK - Additional metadata about the database, such as the type
  private final String _namespace;
  private final List<TableData> _tables;
  private final IFile _ddlFile;

  public DBData(String namespace, List<TableData> tables, IFile ddlFile) {
    _namespace = namespace;
    _tables = Collections.unmodifiableList(new ArrayList<TableData>(tables));
    _ddlFile = ddlFile;
  }

  public String getNamespace() {
    return _namespace;
  }

  public List<TableData> getTables() {
    return _tables;
  }

  public IFile getDdlFile() {
    return _ddlFile;
  }

  public TableData getTable(String value) {
    for (TableData table : _tables) {
      if (table.getName().equals(value)) {
        return table;
      }
    }
    return null;
  }
}
