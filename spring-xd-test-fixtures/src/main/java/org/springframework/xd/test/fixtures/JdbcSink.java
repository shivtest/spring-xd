/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.test.fixtures;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;


/**
 * Represents the {@code jdbc} sink module. Maintains an in memory relational database and exposes a
 * {@link JdbcTemplate} so that assertions can be made against it.
 *
 * @author Florent Biville
 * @author Glenn Renfro
 */
public class JdbcSink extends AbstractModuleFixture implements Disposable {

	private JdbcTemplate jdbcTemplate;

	private String tableName;

	private String columns;

	private volatile DataSource dataSource;

	/**
	 * Initializes a JdbcSink with the {@link DataSource}. Using this DataSource a JDBCTemplate is created.
	 *
	 * @param dataSource
	 */
	public JdbcSink(DataSource dataSource) {
		Assert.notNull(dataSource, "Datasource can not be null");
		this.dataSource = dataSource;
		jdbcTemplate = new JdbcTemplate(dataSource);
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	/**
	 * Renders the DSL for this fixture.
	 */
	@Override
	protected String toDSL() {
		StringBuilder dsl = new StringBuilder();
		try {
			dsl.append("jdbc --initializeDatabase=true --url=" + dataSource.getConnection().getMetaData().getURL());
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not get URL from connection metadata", e);
		}
		if (tableName != null) {
			dsl.append(" --tableName=" + tableName);
		}
		if (columns != null) {
			dsl.append(" --columns=" + columns);
		}
		return dsl.toString();
	}

	/**
	 * If using a in memory database this method will shutdown the database.
	 */
	@Override
	public void cleanup() {
		jdbcTemplate.execute("SHUTDOWN");
	}

	/**
	 * Sets the table that the sink will write to.
	 *
	 * @param tableName the name of the table.
	 * @return an instance to this jdbc sink.
	 */
	public JdbcSink tableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	/**
	 * allows a user to set the columns (comma delimited list) that the sink will write its results to.
	 *
	 * @param columns a comma delimited list of column names.
	 * @return an instance to this jdbc sink.
	 */
	public JdbcSink columns(String columns) {
		this.columns = columns;
		return this;
	}


	/**
	 * Determines if a connection to the designated database can be made.
	 *
	 * @return true if a connection can be made. False if not.
	 */
	public boolean isReady() {
		boolean result = true;
		Connection conn = null;
		try {
			conn = getJdbcTemplate().getDataSource().getConnection();
		}
		catch (Exception ex) {
			result = false;
		}
		finally {
			if (conn != null) {
				try {
					conn.close();
				}
				catch (SQLException se) {
					// ignore exception. Sorry PMD.
				}
			}
		}
		return result;
	}

}
