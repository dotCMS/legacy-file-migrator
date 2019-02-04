package com.dotmarketing.osgi.migrator;

import java.sql.Savepoint;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.Permission;
import com.dotmarketing.beans.VersionInfo;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.IdentifierAPI;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.business.VersionableAPI;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.factories.InodeFactory;
import com.dotmarketing.osgi.migrator.data.LegacyFile;
import com.dotmarketing.osgi.migrator.data.MigratorDAO;
import com.dotmarketing.portlets.categories.model.Category;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.DotContentletStateException;
import com.dotmarketing.portlets.contentlet.business.DotContentletValidationException;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.portlets.files.model.File;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.structure.factories.StructureFactory;
import com.dotmarketing.portlets.structure.model.ContentletRelationships;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.model.User;

/**
 * This service is in charge of retrieving all the legacy files from all the
 * different Sites in a dotCMS DB and transforming them into Files as Content
 * data. These new Files are the current way dotCMS handles files, and it is
 * imperative to store them that way as Legacy Files are not handle by dotCMS
 * 4.x at any level anymore.
 * 
 * @author Jose Orsini, Jose Castro
 * @version 3.3, 3.7.1
 * @since Aug 28th, 2017
 *
 */
public class LegacyFilesMigrator {

	private static ContentletAPI contAPI;
	private static LanguageAPI langAPI;
	private static IdentifierAPI identifierAPI;
	private static UserAPI userAPI;
	private static VersionableAPI versionableAPI;
	private static User SYSTEM_USER;
	private static Structure fileAssetContentType;
	private static Configuration pluginConfig;
	private static MigratorDAO migratorDAO;
	private static PermissionAPI permissionAPI;
	private static HostAPI hostAPI;
	private static FolderAPI folderAPI;

	private static final boolean RESPECT_FRONTEND_ROLES = Boolean.TRUE;
	private static final boolean RESPECT_ANON_PERMISSIONS = Boolean.TRUE;

	private static boolean STOP_PROCESS = Boolean.FALSE;

	/**
	 * Default class constructor. Initializes the different APIs required to perform
	 * the Legacy Files transformation.
	 */
	public LegacyFilesMigrator() {
		contAPI = APILocator.getContentletAPI();
		langAPI = APILocator.getLanguageAPI();
		identifierAPI = APILocator.getIdentifierAPI();
		userAPI = APILocator.getUserAPI();
		versionableAPI = APILocator.getVersionableAPI();
		permissionAPI = APILocator.getPermissionAPI();
		hostAPI = APILocator.getHostAPI();
		folderAPI = APILocator.getFolderAPI();
		pluginConfig = MigratorUtils.initPluginConfig();
		migratorDAO = MigratorDAO.getInstance(pluginConfig);
	}

	/**
	 * This is the main routine that starts the Legacy Files transformation process.
	 * All files form all sites will be converted to Files as Content. However, for
	 * files located under the System Host, they will be deleted as no File as
	 * Content can be created inside System Host.
	 */
	public void startMigration() {
		Logger.info(this.getClass(),
				" \n \n" + "=======================================================================\n"
						+ "===== Initializing conversion of Legacy Files to Files as Content =====\n"
						+ "=======================================================================\n");
		try {
			migratorDAO.recreateMissingParentPaths();
			SYSTEM_USER = userAPI.getSystemUser();
			fileAssetContentType = getFileAssetContentType();
			int migrated = 0;
			int counter = 0;
			final long startTimeInMills = System.currentTimeMillis();
			final Date now = new Date(startTimeInMills);
			logStartInfo(now);
			final List<LegacyFile> legacyFiles = new ArrayList<>();
			migratorDAO.fillLegacyFileQueue(legacyFiles);
			if (legacyFiles.isEmpty()) {
				Logger.info(this.getClass(),
						"There are no Legacy Files in this dotCMS instance. Terminating migration process...");
				return;
			}
			while (Boolean.TRUE) {
				if (!legacyFiles.isEmpty()) {
					StringBuilder logging = new StringBuilder();
					boolean startBatchTransaction = Boolean.TRUE;
					for (final LegacyFile legacyFile : legacyFiles) {
						if (STOP_PROCESS) {
							break;
						}
						counter++;
						if (startBatchTransaction) {
							startBatchTransaction = Boolean.FALSE;
							HibernateUtil.startTransaction();
						}
						logging.append("   " + counter + ". Legacy file ID: " + legacyFile.getIdentifier() + "\n");
						try {
							migratorDAO.updateLegacyFileRecord(legacyFile, MigrationStatus.PROCESSING);
							migrateLegacyFile(legacyFile);
							migratorDAO.updateLegacyFileRecord(legacyFile, MigrationStatus.MIGRATED);
							migrated++;
						} catch (final Exception e) {
							Logger.error(this,
									String.format("An error occurred when migrating file with Identifier: '%s'",
											legacyFile.getIdentifier()),
									e);
							try {
								migratorDAO.updateLegacyFileRecord(legacyFile, MigrationStatus.ERROR, e);
							} catch (final Exception e2) {
								Logger.warn(this,
										"An error occurred with the DB transaction when migrating Legacy file ID: "
												+ legacyFile.getIdentifier()
												+ ". Forcing the creation of a new transaction to continue with the migration process...");
								startBatchTransaction = Boolean.TRUE;
							}
						} finally {
							if (counter == legacyFiles.size()
									|| (counter > 0 && counter % pluginConfig.getLoggingFrequency() == 0)) {
								Logger.info(this.getClass(), "\n" + logging.toString());
								logging = new StringBuilder();
							}
						}
					}
					// If not all logging data has been written, then write it
					if (UtilMethods.isSet(logging.toString())) {
						Logger.info(this.getClass(), "\n" + logging.toString());
						logging = new StringBuilder();
					}
					HibernateUtil.commitTransaction();
					Logger.info(this.getClass(), "Migrated files have been committed. Retrieving next batch...");
					legacyFiles.clear();
					if (STOP_PROCESS) {
						Logger.info(this.getClass(), "The migration process has been manually stopped.");
						break;
					}
					migratorDAO.fillLegacyFileQueue(legacyFiles);
				} else {
					// There are no more Legacy Files to migrate, log shutdown info
					logShutdownInfo(startTimeInMills, migrated);
					break;
				}
			}
		} catch (final Exception ex) {
			try {
				Logger.error(this.getClass(), "An error occurred when migrating Files to Contents.", ex);
				HibernateUtil.rollbackTransaction();
			} catch (final DotHibernateException e1) {
				Logger.warn(this, "An error occurred when rolling back the DB transcation: " + e1.getMessage(), e1);
			}
		} finally {
			try {
				HibernateUtil.closeSession();
			} catch (final DotHibernateException e) {
				Logger.error(this.getClass(), "An error occurred when closing the Hibernate session.", e);
			}
		}
	}

	/**
	 * Stops the migration process in case the plugin is manually un-deployed or
	 * stopped.
	 */
	public void stopMigration() {
		STOP_PROCESS = Boolean.TRUE;
	}

	/**
	 * Performs the migration of the legacy file to the new file as content. The DB
	 * information associated to the Legacy File and its physical file can be
	 * manually deleted <b>after the legacy data is migrated correctly</b>. This
	 * will assist the migration process in terms of performance as fewer operations
	 * (DB and I/O) are executed.
	 * <p>
	 * It's also worth noting that the new File as Content will have the live
	 * version of the Legacy File. This means that the new file will have the same
	 * Identifier and Inode of the old one. All other versions of the file need to
	 * be manually discarded.
	 * </p>
	 * 
	 * @param legacyFile
	 *            - The legacy file to migrate.
	 * @return Returns {@code true} if the process was successful.
	 * @throws Exception
	 *             An error occurred when migrating the specified Legacy File.
	 */
	public boolean migrateLegacyFile(final LegacyFile legacyFile) throws Exception {
		// Retrieve Identifier object of the legacy file
		final Identifier legacyIdentifier = identifierAPI.find(legacyFile.getIdentifier());
		if (null == legacyIdentifier || !UtilMethods.isSet(legacyIdentifier.getId())) {
			Logger.warn(this, String.format("Legacy File with ID (%s) is not present in the Identifier table anymore.",
					legacyFile.getIdentifier()));
			return Boolean.FALSE;
		}
		final Savepoint savepoint = HibernateUtil.setSavepoint();
		try {
			if (!isValidLegacyFile(legacyIdentifier)) {
				// Delete any Legacy File flagged as "invalid"
				final File workingFile = (File) versionableAPI.findWorkingVersion(legacyIdentifier, SYSTEM_USER,
						!RESPECT_ANON_PERMISSIONS);
				deleteLegacyFile(workingFile, legacyIdentifier);
				return Boolean.FALSE;
			}
			Contentlet fileAsContent = null;
			File legacyFileFromDb = null;
			final VersionInfo versionInfo = versionableAPI.getVersionInfo(legacyIdentifier.getId());
			// If live and working versions of the Legacy File are different...
			if (versionInfo.getLiveInode() != null
					&& !versionInfo.getLiveInode().equals(versionInfo.getWorkingInode())) {
				legacyFileFromDb = (File) versionableAPI.findLiveVersion(legacyIdentifier, SYSTEM_USER,
						!RESPECT_ANON_PERMISSIONS);
				// Create a Contentlet object using the properties of the LIVE version of the
				// Legacy File
				fileAsContent = copyLegacyFileToContentlet(legacyFileFromDb, legacyIdentifier);
				if (!MigratorUtils.isBinaryFilePresent(fileAsContent)) {
					Logger.warn(this, String.format(
							"Legacy File '%s%s' (%s) is not associated to a valid binary file. Deleting file...",
							legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(),
							legacyIdentifier.getId()));
					// The binary file doesn't exist, delete the Legacy File altogether
					deleteLegacyFile(legacyFileFromDb, legacyIdentifier);
					return Boolean.FALSE;
				}
			} else {
				// Create a Contentlet object using the properties of the WORKING version of the
				// Legacy File
				legacyFileFromDb = (File) versionableAPI.findWorkingVersion(legacyIdentifier, SYSTEM_USER,
						!RESPECT_ANON_PERMISSIONS);
				fileAsContent = copyLegacyFileToContentlet(legacyFileFromDb, legacyIdentifier);
				if (!MigratorUtils.isBinaryFilePresent(fileAsContent)) {
					Logger.warn(this, String.format(
							"Legacy File '%s%s' (%s) is not associated to a valid binary file. Deleting file...",
							legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(),
							legacyIdentifier.getId()));
					// The binary file doesn't exist, delete the Legacy File altogether
					deleteLegacyFile(legacyFileFromDb, legacyIdentifier);
					return Boolean.FALSE;
				}
			}
			// TODO: What is this code doing here?
			if (!permissionAPI.isInheritingPermissions(legacyFileFromDb)) {
				final boolean bitPermissions = Boolean.TRUE;
				final boolean onlyIndividualPermissions = Boolean.TRUE;
				final boolean forceLoadFromDB = Boolean.TRUE;
				permissionAPI.getPermissions(legacyFileFromDb, bitPermissions, onlyIndividualPermissions,
						forceLoadFromDB);
			}
			// Shallow deletion of legacy file
			deleteLegacyFile(legacyFileFromDb, legacyIdentifier);
			HibernateUtil.getSession().clear();
			CacheLocator.getIdentifierCache().removeFromCacheByIdentifier(legacyIdentifier.getId());
			try {
				// If a live and working versions of the Legacy Files are different, check in
				// the live version
				checkinFileAsContentlet(fileAsContent, versionInfo);
			} catch (final DotContentletValidationException e) {
				Logger.warn(this, "\nLegacy File '" + legacyIdentifier.getPath() + "' (" + legacyIdentifier.getId()
						+ ") has invalid fields. Re-trying to check-in without validation...");
				fileAsContent.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
				try {
					checkinFileAsContentlet(fileAsContent, versionInfo);
					Logger.warn(this, "Check-in without validation ran successfully.");
				} catch (final Exception e2) {
					// Non-validated version checkin failed too, throw exception
					Logger.warn(this, "Check-in without validation failed again.");
					throw e2;
				}
			}
		} catch (final Exception e) {
			// The file failed to be migrated. Just roll back to the Save Point and move on
			// to the next file
			Logger.error(this,
					String.format("An error occured when migrating file '%s'. Rolling back to previous SavePoint.",
							legacyFile.getIdentifier()));
			HibernateUtil.rollbackSavepoint(savepoint);
			throw e;
		}
		return Boolean.TRUE;
	}

	/**
	 * Looks up the Content Type object associated to the velocity variable name for
	 * the File Asset Content Type.
	 * 
	 * @return The File Asset Content Type object.
	 * @throws DotSecurityException
	 *             The specified user does not have permissions to access the File
	 *             Asset Content Type.
	 * @throws DotDataException
	 *             An error occurred when interacting with the data source.
	 */
	private Structure getFileAssetContentType() throws DotSecurityException, DotDataException {
		final Structure contentType = StructureFactory
				.getStructureByVelocityVarName(FileAssetAPI.DEFAULT_FILE_ASSET_STRUCTURE_VELOCITY_VAR_NAME);
		if (!permissionAPI.doesUserHavePermission(contentType, PermissionAPI.PERMISSION_READ, SYSTEM_USER)) {
			throw new DotSecurityException(
					"User [" + SYSTEM_USER.getUserId() + "] does not have permission to access Content Type "
							+ FileAssetAPI.DEFAULT_FILE_ASSET_STRUCTURE_VELOCITY_VAR_NAME);
		}
		return contentType;
	}

	/**
	 * Performs a series of validations on the Legacy File in order to determine if
	 * it's worth it to migrate it or not. Usually, a file that has been flagged as
	 * "invalid" will be deleted altogether. There are different situations that can
	 * flag a Legacy File as invalid:
	 * <ul>
	 * <li>The file lives under SYSTEM_HOST.</li>
	 * <li>The file has an invalid asset name.</li>
	 * <li>The file lives under an invalid Site or a content that is NOT a
	 * Site.</li>
	 * <li>One or more of the folders that make up the parent path of the file are
	 * missing.</li>
	 * <ul>
	 * 
	 * @param legacyIdentifier
	 *            - The {@link Identifier} of the Legacy File Asset.
	 * @return If the file complies with the business requirements, returns
	 *         {@code true}. Otherwise, returns {@code false}.
	 * @throws DotStateException
	 * @throws DotDataException
	 *             An error occurred when accessing the data source.
	 * @throws DotSecurityException
	 *             The specified user does not have the required permissions to
	 *             perform this action.
	 */
	private boolean isValidLegacyFile(final Identifier legacyIdentifier)
			throws DotStateException, DotDataException, DotSecurityException {
		// If the Site containing the file is SYSTEM_HOST
		if (Host.SYSTEM_HOST.equals(legacyIdentifier.getHostId())) {
			Logger.warn(this, String.format("Host ID of Legacy File '%s%s' (%s) is SYSTEM_HOST. Deleting file...",
					legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(), legacyIdentifier.getId()));
			return Boolean.FALSE;
		}
		// If the file name is "."
		if (".".equals(legacyIdentifier.getAssetName())) {
			Logger.warn(this, String.format("Legacy File '%s%s' (%s) has an invalid name. Deleting file...",
					legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(), legacyIdentifier.getId()));
			return Boolean.FALSE;
		}
		// If the Site containing the file is not a valid Site
		final Host parentSite = hostAPI.find(legacyIdentifier.getHostId(), SYSTEM_USER, !RESPECT_FRONTEND_ROLES);
		if (null == parentSite || !UtilMethods.isSet(parentSite.getIdentifier())) {
			Logger.warn(this, String.format(
					"Host ID of Legacy File '%s%s' (%s) is not pointing to a valid Site: '%s'. Deleting file...",
					legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(), legacyIdentifier.getId(),
					legacyIdentifier.getHostId()));
			return Boolean.FALSE;
		}
		// If the parent folder is not valid, i.e., any of the folders in the path is missing
		final Folder parentFolder = folderAPI.findFolderByPath(legacyIdentifier.getParentPath(),
				legacyIdentifier.getHostId(), SYSTEM_USER, !RESPECT_FRONTEND_ROLES);
		if (null == parentFolder || !UtilMethods.isSet(parentFolder.getInode())) {
			Logger.warn(this,
					String.format("One or more folders in path '%s' for file '%s' (%s) is missing. Deleting file...",
							legacyIdentifier.getParentPath(), legacyIdentifier.getAssetName(),
							legacyIdentifier.getId()));
			return Boolean.FALSE;
		}
		return Boolean.TRUE;
	}

	/**
	 * Performs a shallow delete of a Legacy File {@link File} object. This specific
	 * operation will only delete the Inode and the Identifier associated to the
	 * file. For performance purposes, not all associated data is deleted yet. It's
	 * worth noting that the DB constraints for the related tables must have been
	 * dropped before running the migration process.
	 * 
	 * @param file
	 *            - The Legacy File that will be deleted.
	 * @param legacyIdentifier
	 *            - The Identifier of the Legacy File.
	 * @throws DotDataException
	 *             An error occurred when deleting the Legacy File.
	 */
	private void deleteLegacyFile(final File file, final Identifier legacyIdentifier) throws DotDataException {
		InodeFactory.deleteInode(file);
		identifierAPI.delete(legacyIdentifier);
	}

	/**
	 * Checks in the working version of the specified file as content. Its version
	 * info will determine whether the content will be published or not.
	 * 
	 * @param contentlet
	 *            - The working version of the content.
	 * @param versionInfo
	 *            - The version info of the content.
	 * @throws DotContentletValidationException
	 *             One or more fields in the content have invalid values.
	 * @throws DotContentletStateException
	 *             An error occurred when saving the content.
	 * @throws IllegalArgumentException
	 *             An error occurred when saving the content.
	 * @throws DotDataException
	 *             An error occurred when interacting with the data source.
	 * @throws DotSecurityException
	 *             The specified user does not have permissions to perform this
	 *             action.
	 */
	private void checkinFileAsContentlet(final Contentlet contentlet, final VersionInfo versionInfo)
			throws DotContentletValidationException, DotContentletStateException, IllegalArgumentException,
			DotDataException, DotSecurityException {
		final Contentlet savedContentlet = contAPI.checkin(contentlet, new ContentletRelationships(contentlet),
				new ArrayList<Category>(), new ArrayList<Permission>(), SYSTEM_USER, !RESPECT_FRONTEND_ROLES);
		if (versionInfo.getLiveInode() != null && versionInfo.getLiveInode().equals(savedContentlet.getInode())) {
			contAPI.publish(savedContentlet, SYSTEM_USER, !RESPECT_FRONTEND_ROLES);
		}
	}

	/**
	 * Takes a legacy file and assigns its properties and binary file to the
	 * Contentlet that will represent it from now on.
	 * 
	 * @param file
	 *            - The legacy file to transform.
	 * @param legacyIdentifier
	 *            - The Identifier object of the Legacy File.
	 * @return The new File as Content.
	 * @throws Exception
	 *             An error occurred when setting some contentlet's properties.
	 */
	private Contentlet copyLegacyFileToContentlet(final File file, final Identifier legacyIdentifier) throws Exception {
		final Contentlet fileAsContent = new Contentlet();
		fileAsContent.setStructureInode(fileAssetContentType.getInode());
		fileAsContent.setLanguageId(langAPI.getDefaultLanguage().getId());
		fileAsContent.setInode(file.getInode());
		fileAsContent.setIdentifier(legacyIdentifier.getId());
		fileAsContent.setModUser(file.getModUser());
		fileAsContent.setModDate(file.getModDate());
		fileAsContent.setStringProperty("title", file.getFileName());
		fileAsContent.setStringProperty("fileName", file.getFileName());
		fileAsContent.setStringProperty("description", file.getTitle());
		fileAsContent.setHost(legacyIdentifier.getHostId());
		final Folder parentFolder = folderAPI.findFolderByPath(legacyIdentifier.getParentPath(),
				legacyIdentifier.getHostId(), SYSTEM_USER, !RESPECT_FRONTEND_ROLES);
		fileAsContent.setFolder(parentFolder.getInode());
		fileAsContent.setBinary("fileAsset", MigratorUtils.getBinaryFileFromLegacyFile(file));
		return fileAsContent;
	}

	/**
	 * Prints basic startup information of the migration process in the log file.
	 * 
	 * @param startDate
	 *            - The date/time when the migration process started.
	 * @throws DotDataException
	 *             An error occurred when accessing the data source.
	 */
	private void logStartInfo(final Date startDate) throws DotDataException {
		final String totalCountLegacyFiles = migratorDAO.getCountPendingLegacyFiles();
		final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Logger.info(this.getClass(), "---> Number of Legacy Files left to migrate: " + totalCountLegacyFiles);
		Logger.info(this.getClass(), "---> Batch size: " + pluginConfig.getBatchSize());
		Logger.info(this.getClass(), "---> Instance ID: " + ConfigUtils.getServerId());
		Logger.info(this.getClass(), "---> Start time: " + sdf.format(startDate) + "\n");
	}

	/**
	 * Prints basic shutdown information of the migration process in the log file.
	 * 
	 * @param startTimeInMills
	 *            - The time in milliseconds when the process started.
	 * @param totalMigratedLegacyFiles
	 *            - The total number of Legacy Files that were migrated.
	 */
	private void logShutdownInfo(final long startTimeInMills, final int totalMigratedLegacyFiles) {
		final long endTimeInMills = System.currentTimeMillis();
		final Date endDate = new Date(endTimeInMills);
		final long totalTimeInMills = endTimeInMills - startTimeInMills;
		final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Logger.info(this.getClass(), "---> End time: " + sdf.format(endDate) + "\n");
		Logger.info(this.getClass(), "---> Total time: " + MigratorUtils.getDurationBreakdown(totalTimeInMills) + "\n");
		Logger.info(this.getClass(),
				" \n" + "\n-> Total processed files = " + totalMigratedLegacyFiles + "\n \n"
						+ "All legacy files have been processed. Please undeploy the Legacy File Migrator plugin now.\n"
						+ " \n");
	}

}
