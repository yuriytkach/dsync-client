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
import com.yet.dsync.util.Config;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConfigDao {

    public static final String CREATE_TABLE_STATEMENT = "CREATE TABLE CONFIG ("
            + "KEY    TEXT PRIMARY KEY  NOT NULL,"
            + "VALUE  TEXT              NOT NULL"
            + ")";

    public static final String YES = "yes";
    public static final String NO = "no";

    private static final String SELECT_STATEMENT = "SELECT VALUE FROM CONFIG WHERE KEY = ?";
    private static final String INSERT_STATEMENT = "INSERT INTO CONFIG (KEY,VALUE) VALUES (?,?)";
    private static final String UPDATE_STATEMENT = "UPDATE CONFIG SET VALUE = ? WHERE KEY = ?";

    private final PreparedStatement readStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;

    public ConfigDao(final Connection connection) {
        try {
            readStatement = connection.prepareStatement(SELECT_STATEMENT);
            insertStatement = connection.prepareStatement(INSERT_STATEMENT);
            updateStatement = connection.prepareStatement(UPDATE_STATEMENT);
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public String read(final Config key) {
        try {
            readStatement.setString(1, key.name());

            final ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(1);
            } else {
                return StringUtils.EMPTY;
            }
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public void write(final Config key, final String value) {
        try {
            readStatement.setString(1, key.name());

            final ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                updateStatement.setString(1, value);
                updateStatement.setString(2, key.name());

                updateStatement.executeUpdate();
            } else {
                insertStatement.setString(1, key.name());
                insertStatement.setString(2, value);

                insertStatement.executeUpdate();
            }
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

}
