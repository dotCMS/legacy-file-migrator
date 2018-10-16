package com.dotmarketing.osgi.migrator;

/**
 * Specifies the different steps that a Legacy File goes through during the
 * migration process:
 * <ul>
 * <li><code>PROCESSING</code>: The file is currently undergoing its
 * transformation to File as Content.</li>
 * <li><code>MIGRATED</code>: The file was successfully migrated to
 * content.</li>
 * <li><code>ERROR</code>: An error occurred during the file's
 * transformation.</li>
 * </ul>
 * 
 * @author Jose Castro
 * @version 3.7.1
 * @since Oct 3, 2018
 *
 */
public enum MigrationStatus {

	PROCESSING(1), MIGRATED(2), ERROR(3);

	private int status;

	private MigrationStatus(final int status) {
		this.status = status;
	}

	/**
	 * Returns the numeric representation of this migration status.
	 * 
	 * @return The numeric representation of this migration status.
	 */
	public int getStatus() {
		return this.status;
	}

}
