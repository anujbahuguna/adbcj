

# ADBCJ Build Guide #

To build ADBCJ, first checkout the code from GitHub

```
git clone git://github.com/mheath/adbcj.git
```

Part of the ADBCJ build includes running a suite of compatibility tests against a MySQL database and a Postgresql database.  The tests will currently connect to the database 'adbcjtck' on 'localhost' with the username 'adbcjtck' and the password 'adbcjtck'.

## MySQL ##

The MySQL schema is located at:
[tck/src/schema/sql/mysql.sql](http://code.google.com/p/adbcj/source/browse/adbcj/trunk/tck/src/schema/sql/mysql.sql)

This SQL script will create the 'adbcj' user and database.  It will then import the database schema needed for running the ADBCJ TCK.  To run this SQL script from the command line do:

```
mysql -u ADMIN_USER -p < tck/src/schema/sql/mysql.sql
```

where ADMIN\_USER is a MySQL user with the necessary privileges to create a user and database.

This assumes, of course, that you have MySQL installed locally.

## Postgresql ##

Importing the schema for Postgresql is a little more difficult.  You can create a Postgresql user that does not have superuser nor create user rights with:

```
createuser adbcjtck -P -S -d -R
```

Upon running this, you will be prompted for a password for the 'adbcjtck' role.  Enter 'adbcjtck'.

To then create the adbcjtck database, type:
```
createdb -h localhost -U adbcjtck -W adbcjtck
```

Enter the password 'adbcjtck' and the adbcjtck database will be created.

To import the adbcjtck schema enter:
```
psql -h localhost -U adbcjtck -W < tck/src/schema/sql/postgresql.sql
```

Enter the password 'adbcjtck' and you should be ready to run the ADBCJ build.

## Building ##

Once these schema files have been imported into their respective databases, you should be able to run the ADBCJ build using [Maven 2](http://maven.apache.org/) doing:

```
mvn install
```