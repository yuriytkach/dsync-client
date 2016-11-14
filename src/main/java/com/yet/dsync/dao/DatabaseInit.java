/*
 * Copyright (C) 2016  Yuriy Tkach
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.    
 */

package com.yet.dsync.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.yet.dsync.exception.DSyncClientException;

public class DatabaseInit {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    public DatabaseInit() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new DSyncClientException(e);
        }
    }
    
    public Connection createConnection(String dbFolder, String dbName) {
        try {
            Connection connection = DriverManager.getConnection(JDBC_PREFIX + dbFolder + File.separator + dbName);
            
            return connection;
        } catch (SQLException e) {
            throw new DSyncClientException(e);
        }
    }
    
    public void createTables(Connection connection) {
        try {
            createConfigTable(connection);
            
            System.out.println("Tables created successfully");
        } catch (SQLException e) {
            throw new DSyncClientException(e);
        }
    }

    private void createConfigTable(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        String sql = "CREATE TABLE CONFIG (" +
                     "KEY    TEXT PRIMARY KEY  NOT NULL," +
                     "VALUE  TEXT              NOT NULL" +
                     ")"; 
        stmt.executeUpdate(sql);
        stmt.close();
    }

}
