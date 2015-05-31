# Asynchronous Database Connectivity in Java _(ADBCJ)_ #

ADBCJ is an asynchronous database driver API for the Java platform.  ADBCJ is similar to [JDBC](http://java.sun.com/docs/books/tutorial/jdbc/) in that it is an API for SQL based database interaction.  The key differentiator is that with ADBCJ, connecting to a database, executing SQL queries, starting and stopping transactions, and disconnecting from the database is all done without blocking.

ADBCJ is a project for [Mike Heath's master's thesis](http://cs.byu.edu/article/2007-12-06-michael_heaths_ms_thesis_proposal) in the [Computer Science Department](http://cs.byu.edu/) at [Brigham Young University](http://byu.edu/).  It is currently in the experimental phase of development.

Currently there is an ADBCJ driver that invokes JDBC using a thread pool to achieve concurrency.  There is also a native MySQL driver and a native Postgresql driver.  Both of the native drivers are built on the high performance networking library [Apache MINA](http://mina.apache.org/).  Experimentation is currently under way to measure the performance and resource utilization differences between the thread pooling and MINA based implementations.

The 0.1 release is a milestone release and is **not production ready**.  It only supports int and varchar database types.  It has been tested on MySQL 5.0.45 and PostgreSQL 8.2.6.  This release is intended to get developers using ADBCJ to provide feedback on the API and functionality of ADBCJ.


---

## Source Code ##
The [ADBCJ source code](http://github.com/mheath/adbcj) is hosted at [GitHub](http://github.com).

---

## Documentation ##

A [simple tutorial](Tutorial.md) is available to help you get started with ADBCJ as well as illustrate the key differences between ADBCJ and JDBC.

For information on building ADBCJ, see the [BuildGuide](BuildGuide.md).

You can browse the ADBCJ API ([0.1](http://api.adbcj.org/0.1), [0.2-SNAPSHOT](http://api.adbcj.org/0.2-SNAPSHOT)).  The API documentation is not complete and needs a lot of work.  If you have any questions about the API, please direct them to the [ADBCJ Google Group](http://groups.google.com/group/adbcj).