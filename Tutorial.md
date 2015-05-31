# ADBCJ Tutorial #

Although ADBCJ is very different from JDBC, we strive to stay consistent with many of the JDBC patterns.  Like JDBC, ADBCJ is an API.  The API is not capable of doing much without a database driver.  Currently ADBCJ has three database drivers, a _JDBC_ wrapper, a _MySQL_ driver, and a _Postgresql_ driver.  The driver that is to be used must register itself with the AJDBC API after which, the driver may be used to connect to a database.

In this tutorial, we will connect to the ADBCJ TCK database on a MySQL database.  See the BuildGuide for information on creating the ADBCJ TCK databases.

## Connecting to a database ##

To use the ADBCJ MySQL driver, you will need the following libraries which are contained in the _lib_ directory ADBCJ download:
| adbcj-api-0.1.jar |
|:------------------|
| adbcj-mysql-0.1.jar |
| mina-core-2.0.0-M1-SNAPSHOT.jar |
| slf4j-api-1.4.3.jar |

### Connecting with JDBC ###

To connect to a database with JDBC, you first register the JDBC driver and then connect to the database.

```
// Register the database driver
Class.forName("com.mysql.jdbc.Driver");

// Connect to the database
Connection connection = DriverManager.getConnection("jdbc:mysql://localhost/adbcjtck", "adbcjtck", "adbcjtck");

```

The first line of code loads the class com.mysql.jdbc.Driver which has a static initializer that registers the MySQL JDBC driver with the JDBC driver manager.  The second line of code connects to the remove database using a JDBC URL, user name, and password.

### Connecting with ADBCJ ###

In contrast, using ADBCJ, you first register ADBCJ driver and then create a ConnectionManager which is used to asynchronously establish a database connection.

```
// Register the ADBCJ database driver statically
org.adbcj.mysql.Adbcj.init();

// Or alternatively register the ADBCJ database driver dynamically
Class.forName("org.adbcj.mysql.Adbcj");

// Create a connection manager
ConnectionManager connectionManager = ConnectionManagerProvider.createConnectionManager("adbcj:mysql://localhost/adbcjtck", "adbcjtck", "adbcjtck");

// Connect asynchronously
DbFuture<Connection> future = connectionManager.connect();

// Using the DbFuture you can register a completion listener that will be invoked when the connection has completed
future.addListener(new DbListener<Connection>() {
    public void onCompletion(DbFuture<Connection> future throws Exception {
        Connection connection = future.get();

        // Do something with the connection
    }
});

// Or block until the connection has completed
Connection connection = future.get();

```

With ADBCJ, you have two options for registering a driver.  You can use the static approach by invoking the static method init() on the driver's Adbcj class.  Or you can load the Adbcj class using Class.forName(String) and the class' static initializer will register the driver with the ADBCJ ConnectionManagerProvider.

Once the driver is registered you can create a ConnectionManager using an ADBCJ URL.  The ADBCJ URL is similar to a JDBC URL.

The ConnectionManager holds the database URL, username and password which are subsequently used to establish database connections.

All database interaction is done asynchronously in ADBCJ.  To achieve this, any operation that could block returns a DbFuture object.  The DbFuture object represents the pending database operation.  DbFuture extends java.util.concurrent.Future which means that DbFuture objects can be used to block until the operation completes or cancel the pending operation.  DbFuture is also observable which means that you can register a completion listener that will be invoked when the operation completes.

### Executing Queries ###

Once you have established a connection, you can use it to query the database.  ADBCJ supports two mechanisms for database queries.  You can use a purely event driven approach or you can fetch a result set.  In this section we will address the latter.

To execute a SQL query, simply call Connection.executeQuery(String sql).  This method will return a DbFuture

&lt;ResultSet&gt;

 object.  An ADBCJ ResultSet implements java.util.List

&lt;Row&gt;

 so it should be very familiar to anyone who has used the Java Collections API.  An ADBCJ Row object implements java.util.Map<Object, Value>.  ADBCJ results are far easier to interact with than JDBC result sets.  Row and column indexing is 0 based and not the brain dead 1 based indexing as in JDBC.

The following examples shows how a query can be used.

```
connection.executeQuery("SELECT int_val, str_val FROM simple_values ORDER BY int_val").addListener(new DbListener<ResultSet>() {
    public void onCompletion(ResultSet resultSet) {
        for (Row row : resultSet) {
            System.out.println(row.get(0)); // You can reference a field by index
            System.out.println(row.get("str_val")); // Or reference a field by name
        }
    }
}
```