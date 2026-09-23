package com.helical.mongodb;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MongoJdbcDriverTest {

    @Test
    public void testDriverRegistration() throws SQLException {
        Driver driver = DriverManager.getDriver("mongodb://localhost:27017/test");
        Assert.assertNotNull("MongoJdbcDriver should be registered with DriverManager", driver);
        Assert.assertTrue("Driver should be an instance of MongoJdbcDriver", driver instanceof MongoJdbcDriver);
    }

    @Test
    public void testAcceptsUrl() throws SQLException {
        MongoJdbcDriver driver = new MongoJdbcDriver();

        Assert.assertTrue(driver.acceptsURL("mongodb://localhost:27017/sample"));
        Assert.assertTrue(driver.acceptsURL("mongodb://user:pass@localhost:27017/sample?authSource=admin"));
        Assert.assertTrue(driver.acceptsURL("mongodb+srv://cluster0.mongodb.net/sample"));
        Assert.assertTrue(driver.acceptsURL("jdbc:mongodb://localhost:27017/sample"));
        Assert.assertTrue(driver.acceptsURL("jdbc:mongo://localhost:27017/sample"));

        Assert.assertFalse(driver.acceptsURL("jdbc:mysql://localhost:3306/sample"));
        Assert.assertFalse(driver.acceptsURL("jdbc:postgresql://localhost:5432/sample"));
        Assert.assertFalse(driver.acceptsURL(null));
        Assert.assertFalse(driver.acceptsURL(""));
    }

    @Test
    public void testPropertyInfo() throws SQLException {
        MongoJdbcDriver driver = new MongoJdbcDriver();
        DriverPropertyInfo[] infos = driver.getPropertyInfo("mongodb://localhost:27017/test", new Properties());
        Assert.assertNotNull(infos);
        Assert.assertTrue(infos.length >= 5);
    }

    @Test
    public void testDriverVersion() {
        MongoJdbcDriver driver = new MongoJdbcDriver();
        Assert.assertEquals(1, driver.getMajorVersion());
        Assert.assertEquals(0, driver.getMinorVersion());
    }

    @Test
    public void testMongoResultSetNavigationAndGetters() throws SQLException {
        List<ColumnDefinition> cols = Arrays.asList(
                new ColumnDefinition("id", Types.INTEGER, "INTEGER"),
                new ColumnDefinition("name", Types.VARCHAR, "VARCHAR"),
                new ColumnDefinition("active", Types.BOOLEAN, "BOOLEAN"),
                new ColumnDefinition("score", Types.DOUBLE, "DOUBLE")
        );
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1, "Alice", true, 95.5),
                Arrays.asList(2, "Bob", false, 82.0)
        );

        MongoResultSet rs = new MongoResultSet(cols, rows);
        Assert.assertNotNull(rs.getMetaData());
        ResultSetMetaData meta = rs.getMetaData();
        Assert.assertEquals(4, meta.getColumnCount());
        Assert.assertEquals("name", meta.getColumnName(2));
        Assert.assertEquals(Types.VARCHAR, meta.getColumnType(2));

        Assert.assertTrue(rs.next());
        Assert.assertEquals(1, rs.getInt("id"));
        Assert.assertEquals("Alice", rs.getString("name"));
        Assert.assertTrue(rs.getBoolean("active"));
        Assert.assertEquals(95.5, rs.getDouble("score"), 0.001);
        Assert.assertFalse(rs.wasNull());

        Assert.assertTrue(rs.next());
        Assert.assertEquals(2, rs.getInt(1));
        Assert.assertEquals("Bob", rs.getString(2));
        Assert.assertFalse(rs.getBoolean(3));
        Assert.assertEquals(82.0, rs.getDouble(4), 0.001);

        Assert.assertFalse(rs.next());
        rs.close();
        Assert.assertTrue(rs.isClosed());
    }

    @Test
    public void testHeartbeatSelect1Query() throws SQLException {
        // Test MongoStatement heartbeat evaluation
        MongoStatement stmt = new MongoStatement(null) {
            @Override
            public Connection getConnection() {
                return null;
            }
        };

        ResultSet rs = stmt.executeQuery("SELECT 1");
        Assert.assertNotNull(rs);
        Assert.assertTrue(rs.next());
        Assert.assertEquals(1, rs.getInt(1));
        Assert.assertFalse(rs.next());
        rs.close();

        ResultSet rsDual = stmt.executeQuery("SELECT 1 FROM DUAL;");
        Assert.assertNotNull(rsDual);
        Assert.assertTrue(rsDual.next());
        Assert.assertEquals(1, rsDual.getInt(1));
        rsDual.close();
    }

    @Test
    public void testPreparedStatementHeartbeat() throws SQLException {
        MongoPreparedStatement pstmt = new MongoPreparedStatement(null, "SELECT 1") {
            @Override
            public Connection getConnection() {
                return null;
            }
        };
        ResultSet rs = pstmt.executeQuery();
        Assert.assertNotNull(rs);
        Assert.assertTrue(rs.next());
        Assert.assertEquals(1, rs.getInt(1));
        rs.close();
    }

    @Test
    public void testCompatibilityAliases() throws SQLException {
        Driver mongoDriverAlias = DriverManager.getDriver("jdbc:mongodb://localhost:27017/test");
        Assert.assertNotNull(mongoDriverAlias);
        Assert.assertTrue(mongoDriverAlias.acceptsURL("jdbc:mongodb://localhost:27017/test"));

        Driver mongoAlias = DriverManager.getDriver("jdbc:mongo://localhost:27017/test");
        Assert.assertNotNull(mongoAlias);
        Assert.assertTrue(mongoAlias.acceptsURL("jdbc:mongo://localhost:27017/test"));
    }

    @Test
    public void testDatabaseDriversPropertiesConfiguration() throws Exception {
        Properties props = new Properties();
        File propFile = new File("../../hi-repository/System/Admin/databaseDrivers.properties");
        if (!propFile.exists()) {
            propFile = new File("server/hi-repository/System/Admin/databaseDrivers.properties");
        }
        if (!propFile.exists()) {
            propFile = new File("hi-repository/System/Admin/databaseDrivers.properties");
        }
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            }
            Assert.assertTrue("Should contain com.helical.mongodb.MongoJdbcDriver",
                    props.containsKey("com.helical.mongodb.MongoJdbcDriver"));
            String urlPort = props.getProperty("com.helical.mongodb.MongoJdbcDriver");
            Assert.assertTrue("Should have port 27017", urlPort.contains("27017"));
            Assert.assertTrue("Should have hostName placeholder", urlPort.contains("{{hostName}}"));
            Assert.assertTrue("Should have database placeholder", urlPort.contains("{{database}}"));
        }
    }
}
