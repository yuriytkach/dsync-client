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

import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.exception.DSyncClientException;
import lombok.SneakyThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MetadataDao {

    static final String CREATE_TABLE_STATEMENT = "CREATE TABLE METADATA ("
        + "ID       TEXT PRIMARY KEY  NOT NULL,"
        + "PATH     TEXT              NOT NULL,"
        + "PLOWER   TEXT              NOT NULL,"
        + "LOADED   INTEGER           NOT NULL,"
        + "REV      TEXT,"
        + "SIZE     INTEGER,"
        + "SRVDATE  INTEGER,"
        + "CLIDATE  INTEGER"
        + ")";

    private static final String SELECT_BY_ID_STATEMENT = "SELECT * FROM METADATA WHERE ID = ?";

    private static final String SELECT_NOT_LOADED_STATEMENT = "SELECT * FROM METADATA WHERE LOADED = 0";

    private static final String SELECT_BY_PLOWER_STATEMENT = "SELECT * FROM METADATA WHERE PLOWER = ?";

    private static final String INSERT_STATEMENT = "INSERT INTO METADATA ("
        + "ID,PATH,PLOWER,LOADED,REV,SIZE,SRVDATE,CLIDATE) VALUES (?,?,?,?,?,?,?,?)";

    private static final String UPDATE_LOADED_STATEMENT = "UPDATE METADATA SET LOADED = ? WHERE ID = ?";

    private static final String UPDATE_FIELDS_STATEMENT = "UPDATE METADATA SET PATH = ?,"
        + "PLOWER = ?,"
        + "REV = ?,"
        + "SIZE = ?,"
        + "SRVDATE = ?,"
        + "CLIDATE = ?"
        + " WHERE ID = ?";

    private static final String DELETE_BY_PATH_STATEMENT = "DELETE FROM METADATA WHERE PLOWER = ?";

    private static final int COL_ID = 1;
    private static final int COL_PATH = COL_ID + 1;
    private static final int COL_PATH_LOWER = COL_PATH + 1;
    private static final int COL_LOADED = COL_PATH_LOWER + 1;
    private static final int COL_REV = COL_LOADED + 1;
    private static final int COL_SIZE = COL_REV + 1;
    private static final int COL_SRVDATE = COL_SIZE + 1;
    private static final int COL_CLIDATE = COL_SRVDATE + 1;

    private static final int UPD_PARAM_PATH = 1;
    private static final int UPD_PARAM_PATH_LOWER = UPD_PARAM_PATH + 1;
    private static final int UPD_PARAM_REV = UPD_PARAM_PATH_LOWER + 1;
    private static final int UPD_PARAM_SIZE = UPD_PARAM_REV + 1;
    private static final int UPD_PARAM_SRVDATE = UPD_PARAM_SIZE + 1;
    private static final int UPD_PARAM_CLIDATE = UPD_PARAM_SRVDATE + 1;
    private static final int UPD_PARAM_ID = UPD_PARAM_CLIDATE + 1;

    private final PreparedStatement readByIdStatement;
    private final PreparedStatement readNotLoadedStatement;
    private final PreparedStatement readByPLowerStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateLoadedStatement;
    private final PreparedStatement updateFieldsStatement;
    private final PreparedStatement deleteByPathStatement;

    private final Lock syncLock = new ReentrantLock(true);

    @SneakyThrows
    public MetadataDao(final Connection connection) {
        readByIdStatement = connection.prepareStatement(SELECT_BY_ID_STATEMENT);
        readNotLoadedStatement = connection.prepareStatement(SELECT_NOT_LOADED_STATEMENT);
        readByPLowerStatement = connection.prepareStatement(SELECT_BY_PLOWER_STATEMENT);
        insertStatement = connection.prepareStatement(INSERT_STATEMENT);
        updateLoadedStatement = connection.prepareStatement(UPDATE_LOADED_STATEMENT);
        updateFieldsStatement = connection.prepareStatement(UPDATE_FIELDS_STATEMENT);
        deleteByPathStatement = connection.prepareStatement(DELETE_BY_PATH_STATEMENT);
    }

    @SneakyThrows
    public DropboxFileData readByLowerPath(final String lowerPath) {
        syncLock.lock();
        try {
            readByPLowerStatement.setString(COL_ID, lowerPath);

            try (ResultSet resultSet = readByPLowerStatement.executeQuery()) {
                if (resultSet.next()) {
                    return buildFileData(resultSet);
                } else {
                    return null;
                }
            }
        } finally {
            syncLock.unlock();
        }
    }

    @SneakyThrows
    private DropboxFileData buildFileData(final ResultSet resultSet) {
        final BigDecimal size = resultSet.getBigDecimal(COL_SIZE);
        return DropboxFileData.builder()
            .id(resultSet.getString(COL_ID))
            .pathDisplay(resultSet.getString(COL_PATH))
            .pathLower(resultSet.getString(COL_PATH_LOWER))
            .rev(resultSet.getString(COL_REV))
            .size(size == null ? null : size.longValue())
            .serverModified(longToDateTime(resultSet.getBigDecimal(COL_SRVDATE)))
            .clientModified(longToDateTime(resultSet.getBigDecimal(COL_SRVDATE)))
            .build();
    }

    @SneakyThrows
    public void write(final DropboxFileData fileData) {
        syncLock.lock();
        try {
            readByIdStatement.setString(COL_ID, fileData.getId());

            try (ResultSet resultSet = readByIdStatement.executeQuery()) {
                if (resultSet.next()) {
                    updateFieldsStatement.setString(UPD_PARAM_PATH, fileData.getPathDisplay());
                    updateFieldsStatement.setString(UPD_PARAM_PATH_LOWER, fileData.getPathLower());
                    setStatementParams(updateFieldsStatement, UPD_PARAM_REV,
                        fileData.getRev(), Types.VARCHAR);
                    setStatementParams(updateFieldsStatement, UPD_PARAM_SIZE,
                        fileData.getSize(), Types.BIGINT);
                    setStatementParams(updateFieldsStatement, UPD_PARAM_SRVDATE,
                        dateTimeToLong(fileData.getServerModified()), Types.BIGINT);
                    setStatementParams(updateFieldsStatement, UPD_PARAM_CLIDATE,
                        dateTimeToLong(fileData.getClientModified()), Types.BIGINT);

                    updateFieldsStatement.setString(UPD_PARAM_ID, fileData.getId());

                } else {
                    fillInsertStatement(fileData);

                    insertStatement.executeUpdate();
                }
            }
        } finally {
            syncLock.unlock();
        }
    }

    @SneakyThrows
    private void fillInsertStatement(final DropboxFileData fileData) {
        insertStatement.setString(COL_ID, fileData.getId());
        insertStatement.setString(COL_PATH, fileData.getPathDisplay());
        insertStatement.setString(COL_PATH_LOWER, fileData.getPathLower());
        insertStatement.setBoolean(COL_LOADED, false);
        setStatementParams(insertStatement, COL_REV, fileData.getRev(), Types.VARCHAR);
        setStatementParams(insertStatement, COL_SIZE, fileData.getSize(), Types.BIGINT);
        setStatementParams(insertStatement, COL_SRVDATE,
            dateTimeToLong(fileData.getServerModified()), Types.BIGINT);
        setStatementParams(insertStatement, COL_CLIDATE,
            dateTimeToLong(fileData.getClientModified()), Types.BIGINT);
    }

    @SneakyThrows
    public void write(final Set<DropboxFileData> fileDataSet) {
        syncLock.lock();
        try {
            fileDataSet.forEach(fileData -> {

                try {
                    fillInsertStatement(fileData);
                    insertStatement.addBatch();
                } catch (final SQLException ex) {
                    throw new DSyncClientException(ex);
                }

            });
            insertStatement.executeBatch();
        } finally {
            syncLock.unlock();
        }
    }

    @SneakyThrows
    public Collection<DropboxFileData> readAllNotLoaded() {
        try (ResultSet resultSet = readNotLoadedStatement.executeQuery()) {

            final Collection<DropboxFileData> allFileData = new LinkedList<>();

            while (resultSet.next()) {
                final DropboxFileData fileData = buildFileData(resultSet);
                allFileData.add(fileData);
            }

            return allFileData;
        }
    }

    @SneakyThrows
    public void writeLoadedFlag(final String id) {
        syncLock.lock();
        try {
            updateLoadedStatement.setBoolean(1, Boolean.TRUE);
            updateLoadedStatement.setString(2, id);

            updateLoadedStatement.executeUpdate();
        } finally {
            syncLock.unlock();
        }
    }

    @SneakyThrows
    public void deleteByLowerPath(final String pathLower) {
        syncLock.lock();
        try {
            deleteByPathStatement.setString(1, pathLower);

            deleteByPathStatement.executeUpdate();
        } finally {
            syncLock.unlock();
        }
    }

    @SneakyThrows
    private void setStatementParams(final PreparedStatement statement,
                                    final int column, final Object data, final int sqlType) {
        if (data == null) {
            statement.setNull(column, sqlType);
        } else {
            statement.setObject(column, data, sqlType);
        }
    }

    private Long dateTimeToLong(final LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        } else {
            return dateTime.atZone(ZoneId.of("GMT")).toInstant().toEpochMilli();
        }
    }

    private LocalDateTime longToDateTime(final BigDecimal epochMilli) {
        if (epochMilli == null) {
            return null;
        } else {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli.longValue()), ZoneId.of("GMT"));
        }
    }

}
