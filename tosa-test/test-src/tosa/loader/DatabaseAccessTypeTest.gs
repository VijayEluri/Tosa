package tosa.loader

uses java.io.*
uses java.lang.*
uses gw.lang.reflect.IPropertyInfo
uses gw.lang.reflect.features.PropertyReference
uses org.junit.Assert
uses org.junit.Before
uses org.junit.BeforeClass
uses org.junit.Test
uses test.testdb.Bar
uses test.testdb.SortPage
uses test.testdb.Foo
uses test.testdb.Baz
uses gw.lang.reflect.TypeSystem

class DatabaseAccessTypeTest {

  @BeforeClass
  static function beforeTestClass() {
    TosaTestDBInit.createDatabase()
  }

  @Test
  function testJdbcUrlPropertyGetter() {
    Assert.assertEquals("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", test.testdb.Database.JdbcUrl)
  }

  @Test
  function testJdbcUrlPropertySetterModifiesConnectionUrl() {
    var originalUrl = test.testdb.Database.JdbcUrl
    try {
      // Reset the JdbcUrl
      test.testdb.Database.JdbcUrl = "jdbc:h2:mem:some_other_testdb;DB_CLOSE_DELAY=-1"
      // Now create the tables in that new DB; if the JdbcUrl hasn't actually changed, this will blow up
      test.testdb.Database.createTables()
      var foo = new Foo(){:FirstName = "Charlie", :LastName="Brown", :Address="1234 Main St.\nCentreville, KS 12345"}
      foo.update()
      Assert.assertEquals(1, Foo.count(new Foo(){:FirstName = "Charlie"}))

      // Now switch back to the original DB and verify that's not there
      test.testdb.Database.JdbcUrl = originalUrl
      Assert.assertEquals(0, Foo.count(new Foo(){:FirstName = "Charlie"}))
    } finally {
      test.testdb.Database.JdbcUrl = originalUrl
    }
  }
}