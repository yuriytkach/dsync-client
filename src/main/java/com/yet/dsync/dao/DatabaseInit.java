/*
 * Copyright (c) 2017 Yuriy Tkach
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

import com.yet.dsync.exception.DSyncClientException;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInit {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    public DatabaseInit() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (final ClassNotFoundException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public Connection createConnection(final String dbFolder, final String dbName) {
        try {
            return DriverManager.getConnection(JDBC_PREFIX + dbFolder + File.separator + dbName);
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public void createTables(final Connection connection) {
        try {
            createConfigTable(connection);
            createMetadataTable(connection);
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    private void createConfigTable(final Connection connection) throws SQLException {
        final Statement stmt = connection.createStatement();
        stmt.executeUpdate(ConfigDao.CREATE_TABLE_STATEMENT);
        stmt.close();
    }

    private void createMetadataTable(final Connection connection) throws SQLException {
        final Statement stmt = connection.createStatement();
        stmt.executeUpdate(MetadataDao.CREATE_TABLE_STATEMENT);
        stmt.close();
    }

}
