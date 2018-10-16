package com.dotmarketing.osgi.migrator;

import com.dotcms.repackage.org.apache.commons.lang.StringUtils;

/**
 * Exposes the configuration parameters for this migration plugin. These
 * properties are set via the <code>/src/main/resources/plugin.properties</code>
 * file.
 * 
 * @author Jose Castro
 * @version 3.7.1
 * @since Oct 9, 2018
 *
 */
public class Configuration {

	private String loadQuery;
	private int batchSize;
	private int status;
	private int loggingFrequency;

	/**
	 * Creates an instance of the {@link Configuration} class using the following
	 * default parameters:
	 * <ul>
	 * <li>Batch Size: 50</li>
	 * <li>Initial Status: 1</li>
	 * <li>Logging Frequency: 10</li>
	 * </ul>
	 */
	public Configuration() {
		this(StringUtils.EMPTY, 50, 1, 10);
	}

	/**
	 * Creates an instance of the {@link Configuration} class.
	 * 
	 * @param loadQuery
	 *            - The SQL query that will retrieve a number of Legacy Files to
	 *            migrate.
	 * @param batchSize
	 *            - The number of records that will be retrieved by the previous SQL
	 *            query.
	 * @param status
	 *            - The migration status of the Legacy File records to retrieve,
	 *            i.e., files with a status lower or equal to the specified value
	 *            will be returned.
	 * @param loggingFrequency
	 *            - The number of logging lines that will be sent to the log file in
	 *            order to minimize constant I/O output to the file.
	 */
	public Configuration(final String loadQuery, final int batchSize, final int status, final int loggingFrequency) {
		this.loadQuery = loadQuery;
		this.batchSize = batchSize;
		this.status = status;
		this.loggingFrequency = loggingFrequency;
	}

	/**
	 * Returns the SQL query that loads the next batch of Legacy Files to migrate.
	 * 
	 * @return The load SQL query.
	 */
	public String getLoadQuery() {
		return this.loadQuery;
	}

	/**
	 * Sets the SQL query that loads the next batch of Legacy Files to migrate.
	 * 
	 * @param loadQuery
	 *            - The load SQL query.
	 */
	public void setLoadQuery(String loadQuery) {
		this.loadQuery = loadQuery;
	}

	/**
	 * Returns the number of records that will be fetched by the load SQL query.
	 * 
	 * @return The number of records in the returned batch.
	 */
	public int getBatchSize() {
		return this.batchSize;
	}

	/**
	 * Sets the number of records that will be fetched by the load SQL query.
	 * 
	 * @param batchSize
	 *            - The number of records in the returned batch.
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	/**
	 * Returns the top status of files returned by the load SQL query. This means
	 * that files whose status is lower or equal than the one specified will be
	 * retrieved.
	 * 
	 * @return The migration status of the Legacy File.
	 */
	public int getStatus() {
		return this.status;
	}

	/**
	 * Sets the top status of files returned by the load SQL query. This means that
	 * files whose status is lower or equal than the one specified will be
	 * retrieved.
	 * 
	 * @param status
	 *            - The migration status of the Legacy File.
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * Returns the number of logging lines that will be sent to the log file.
	 * 
	 * @return The number of logging lines.
	 */
	public int getLoggingFrequency() {
		return this.loggingFrequency;
	}

	/**
	 * Sets the number of logging lines that will be sent to the log file.
	 * 
	 * @param loggingFrequency
	 *            - The number of logging lines.
	 */
	public void setLoggingFrequency(int loggingFrequency) {
		this.loggingFrequency = loggingFrequency;
	}

	@Override
	public String toString() {
		return "Configuration [loadQuery=" + loadQuery + ", batchSize=" + batchSize + ", status=" + status
				+ ", loggingFrequency=" + loggingFrequency + "]";
	}

}
