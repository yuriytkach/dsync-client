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
import lombok.SneakyThrows;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInit {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    @SneakyThrows
    public DatabaseInit() {
        Class.forName("org.sqlite.JDBC");
    }

    @SneakyThrows
    public Connection createConnection(final String dbFolder, final String dbName) {
        return DriverManager.getConnection(JDBC_PREFIX + dbFolder + File.separator + dbName);
    }

    @SneakyThrows
    public void createTables(final Connection connection) {
        createConfigTable(connection);
        createMetadataTable(connection);
    }

    @SneakyThrows
    private void createConfigTable(final Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(ConfigDao.CREATE_TABLE_STATEMENT);
        }
    }

    @SneakyThrows
    private void createMetadataTable(final Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(MetadataDao.CREATE_TABLE_STATEMENT);
        }
    }

}
