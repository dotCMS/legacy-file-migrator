package com.dotmarketing.osgi.migrator.data;

import com.dotcms.repackage.org.apache.commons.lang.StringUtils;
import com.dotmarketing.osgi.migrator.MigrationStatus;

/**
 * Represents the basic information of a Legacy File required by the migrator
 * plugin to convert it to a File as Content. It also provides the
 * 
 * @author Jose Castro
 * @version 3.7.1
 * @since Oct 3, 2018
 *
 */
public class LegacyFile {

	private String identifier;
	private String serverId;
	private int status;
	private String errorMsg;

	/**
	 * Creates a new instance of the {@link LegacyFile} class, which represents the
	 * most importante information required to migrate a Legacy File to a File as
	 * Content.
	 */
	public LegacyFile() {
		this(StringUtils.EMPTY, StringUtils.EMPTY, 0, StringUtils.EMPTY);
	}

	/**
	 * Creates a new instance of the {@link LegacyFile} class, which represents the
	 * most importante information required to migrate a Legacy File to a File as
	 * Content.
	 * 
	 * @param identifier
	 *            - The Identifier of the file.
	 * @param serverId
	 *            - The ID of the dotCMS instance that is or will be handling the
	 *            migration of this file.
	 * @param status
	 *            - The migration status of the file, specified by the
	 *            {@link MigrationStatus} Enum.
	 * @param errorMsg
	 *            - The full Java stack trace in case an error occurred during the
	 *            migration. This will help determine the root cause of the problem
	 *            to fix it and run the migration process on a failed record again.
	 */
	public LegacyFile(final String identifier, final String serverId, final int status, final String errorMsg) {
		this.identifier = identifier;
		this.serverId = serverId;
		this.status = status;
		this.errorMsg = errorMsg;
	}

	/**
	 * Returns the Identifier of the file.
	 * 
	 * @return The Identifier of the file.
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * Sets the Identifier of the file.
	 * 
	 * @param identifier
	 *            - The Identifier of the file.
	 */
	public void setIdentifier(final String identifier) {
		this.identifier = identifier;
	}

	/**
	 * Returns the ID of the dotCMS instance that is or will be handling the
	 * migration of this file.
	 * 
	 * @return The ID of the dotCMS instance.
	 */
	public String getServerId() {
		return this.serverId;
	}

	/**
	 * Sets the ID of the dotCMS instance that is or will be handling the migration
	 * of this file.
	 * 
	 * @param serverId
	 *            - The ID of the dotCMS instance.
	 */
	public void setServerId(final String serverId) {
		this.serverId = serverId;
	}

	/**
	 * Returns The migration status of the file, specified by the
	 * {@link MigrationStatus} Enum.
	 * 
	 * @return The migration status of the file.
	 */
	public int getStatus() {
		return this.status;
	}

	/**
	 * Sets The migration status of the file, specified by the
	 * {@link MigrationStatus} Enum.
	 * 
	 * @param status
	 *            - The migration status of the file.
	 */
	public void setStatus(final int status) {
		this.status = status;
	}

	/**
	 * Returns The full Java stack trace in case an error occurred during the
	 * migration. This will help determine the root cause of the problem to fix it
	 * and run the migration process on a failed record again.
	 * 
	 * @return The full Java stack trace.
	 */
	public String getErrorMsg() {
		return this.errorMsg;
	}

	/**
	 * Sets The full Java stack trace in case an error occurred during the
	 * migration. This will help determine the root cause of the problem to fix it
	 * and run the migration process on a failed record again.
	 * 
	 * @param errorMsg
	 *            - The full Java stack trace.
	 */
	public void setErrorMsg(final String errorMsg) {
		this.errorMsg = errorMsg;
	}

	@Override
	public String toString() {
		return "LegacyFileJournal [serverId=" + this.serverId + ", status=" + this.status + ", errorMsg="
				+ this.errorMsg + "]";
	}

}
