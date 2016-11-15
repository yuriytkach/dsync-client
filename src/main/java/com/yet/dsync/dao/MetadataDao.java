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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneId;

import com.yet.dsync.dto.FileData;
import com.yet.dsync.exception.DSyncClientException;

public class MetadataDao {

    private static final String SELECT_BY_ID_STATEMENT = "SELECT * FROM METADATA WHERE ID = ?";
    private static final String INSERT_STATEMENT = "INSERT INTO METADATA (ID,PATH,REV,SIZE,SRVDATE,CLIDATE) VALUES (?,?,?,?,?,?)";
    
    public static final String CREATE_TABLE_STATEMENT = "CREATE TABLE METADATA (" +
                                                            "ID       TEXT PRIMARY KEY  NOT NULL," +
                                                            "PATH     TEXT              NOT NULL," +
                                                            "REV      TEXT," +
                                                            "SIZE     INTEGER," +
                                                            "SRVDATE  INTEGER," +
                                                            "CLIDATE  INTEGER" +
                                                            ")"; 
    
    private PreparedStatement readStatement;
    private PreparedStatement insertStatement;

    public MetadataDao(Connection connection) {
        try {
            readStatement = connection.prepareStatement(SELECT_BY_ID_STATEMENT);
            insertStatement = connection.prepareStatement(INSERT_STATEMENT);
        } catch (SQLException e) {
            throw new DSyncClientException(e);
        }
    }
    
    public FileData read(String id) {
        try {
            readStatement.setString(1, id);
            
            ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                FileData.Builder builder = new FileData.Builder();
                builder
                    .id(resultSet.getString(1))
                    .pathDisplay(resultSet.getString(2));
                // TODO: Build other fields
                return builder.build();
            } else {
                return null;
            }
        } catch (SQLException e) {
            throw new DSyncClientException(e);
        }
    }
    
    public void write(FileData fileData) {
        try {
            readStatement.setString(1, fileData.getId());
            
            ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                // TODO Update
            } else {
                insertStatement.setString(1, fileData.getId());
                insertStatement.setString(2, fileData.getPathDisplay());
                insertStatement.setString(3, fileData.getRev());
                if (fileData.getSize() == null) {
                    insertStatement.setNull(4, Types.BIGINT);
                } else {
                    insertStatement.setLong(4, fileData.getSize());
                }
                if (fileData.getServerModified() == null) {
                    insertStatement.setNull(5, Types.BIGINT);
                } else {
                    insertStatement.setLong(5, fileData.getServerModified().atZone(ZoneId.of("GMT")).toInstant().toEpochMilli());
                }
                
                if (fileData.getClientModified() == null) {
                    insertStatement.setNull(6, Types.BIGINT);
                } else {
                    insertStatement.setLong(6, fileData.getClientModified().atZone(ZoneId.of("GMT")).toInstant().toEpochMilli());
                }
                
                insertStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DSyncClientException(e);
        }
    }

}
