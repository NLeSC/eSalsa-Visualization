package nl.esciencecenter.visualization.esalsa.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class JDBCConnector {
    private Connection connection;

    public JDBCConnector() {
        try {
            // make sure the ClassLoader has the MonetDB JDBC driver loaded
            Class.forName("nl.cwi.monetdb.jdbc.MonetDriver");
            // request a Connection to a MonetDB server running on 'localhost'
            connection = DriverManager.getConnection("jdbc:monetdb://es-visual:50000/eSalsa", "monetdb", "monetdb");

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void readTestData() {
        try {
            Statement st = connection.createStatement();
            ResultSet rs;

            rs = st.executeQuery("SELECT value FROM ssh1 WHERE t_lat = 0;");

            // get meta data and print columns with their type
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                System.out.print(md.getColumnName(i) + ":" + md.getColumnTypeName(i) + "\t");
            }

            System.out.println("");

            // print the data: only the first 5 rows, while there probably are
            // a lot more. This shouldn't cause any problems afterwards since
            // the
            // result should get properly discarded on the next query
            for (int i = 0; rs.next() && i < 5; i++) {
                for (int j = 1; j <= md.getColumnCount(); j++) {
                    System.out.print(rs.getString(j) + "\t");
                }
                System.out.println("");
            }

            // tell the driver to only return 5 rows, it can optimize on this
            // value, and will not fetch any more than 5 rows.
            st.setMaxRows(5);
            // we ask the database for 22 rows, while we set the JDBC driver to
            // 5 rows, this shouldn't be a problem at all...
            rs = st.executeQuery("select * from ssh1 limit 22");
            // read till the driver says there are no rows left
            for (int i = 0; rs.next(); i++) {
                System.out.print("[" + rs.getString("t_lon") + "]");
            }

            // this close is not needed, should be done by next execute(Query)
            // call
            // however if there can be some time between this point and the next
            // execute call, it is from a resource perspective better to close
            // it.
            rs.close();

            // // unset the row limit; 0 means as much as the database sends us
            // st.setMaxRows(0);
            // // we only ask 10 rows
            // rs = st.executeQuery("select * from b limit 10;");
            // // and simply print them
            // while (rs.next()) {
            // System.out.print(rs.getInt("rowid") + ", ");
            // System.out.print(rs.getString("id") + ", ");
            // System.out.print(rs.getInt("var1") + ", ");
            // System.out.print(rs.getInt("var2") + ", ");
            // System.out.print(rs.getString("var3") + ", ");
            // System.out.println(rs.getString("var4"));
            // }
            //
            // rs.close();

            // perform a ResultSet-less query (with no trailing ; since that
            // should
            // be possible as well and is JDBC standard)
            // Note that this method should return the number of updated rows.
            // This
            // method however always returns -1, since Monet currently doesn't
            // support returning the affected rows.
            // st.executeUpdate("delete from a where var1 = 'zzzz'");

            st.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
