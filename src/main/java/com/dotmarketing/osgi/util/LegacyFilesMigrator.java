package com.dotmarketing.osgi.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.dotcms.repackage.org.apache.commons.io.FilenameUtils;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.beans.VersionInfo;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.business.IdentifierAPI;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.business.Versionable;
import com.dotmarketing.business.VersionableAPI;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.DotContentletStateException;
import com.dotmarketing.portlets.contentlet.business.DotContentletValidationException;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.files.business.FileAPI;
import com.dotmarketing.portlets.files.model.File;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.structure.factories.StructureFactory;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.util.Logger;
import com.liferay.portal.model.User;
import com.liferay.util.FileUtil;

/**
 * This service is in charge of retrieving all the legacy files from all the
 * different Sites in a dotCMS DB and transforming them into Files as Content
 * data. These new Files are the current way dotCMS handles files, and it is
 * imperative to store them that way as Legacy Files are not handle by dotCMS
 * 4.x at any level anymore.
 * 
 * @author Jose Orsini, Jose Castro
 * @version 3.3
 * @since Aug 28th, 2017
 *
 */
public class LegacyFilesMigrator {

	private static ContentletAPI contAPI;
	private static LanguageAPI langAPI;
	private static HostAPI siteAPI;
	private static FileAPI fileAPI;
	private static IdentifierAPI identifierAPI;
	private static PermissionAPI permissionAPI;
	private static UserAPI userAPI;
	private static VersionableAPI versionableAPI;
	private static User sysUser;
	private static Structure fileAssetContentType;

	private static final boolean RESPECT_FRONTEND_ROLES = Boolean.TRUE;
	private static final boolean RESPECT_ANON_PERMISSIONS = Boolean.TRUE;

	/**
	 * Default class constructor. Initializes the different APIs required to
	 * perform the Legacy Files transformation.
	 */
	public LegacyFilesMigrator() {
		contAPI = APILocator.getContentletAPI();
		langAPI = APILocator.getLanguageAPI();
		siteAPI = APILocator.getHostAPI();
		fileAPI = APILocator.getFileAPI();
		identifierAPI = APILocator.getIdentifierAPI();
		permissionAPI = APILocator.getPermissionAPI();
		userAPI = APILocator.getUserAPI();
		versionableAPI = APILocator.getVersionableAPI();
	}

	/**
	 * This is the main routine that starts the Legacy Files transformation
	 * process. All files form all sites will be converted to Files as Content.
	 * However, for files located under the System Host, they will be deleted as
	 * no File as Content can be created inside System Host.
	 */
	public void migrateLegacyFiles() {
		Logger.info(this.getClass(),
				" \n \n" + "=======================================================================\n"
						+ "===== Initializing conversion of Legacy Files to Files as Content =====\n"
						+ "=======================================================================\n");
		try {
			recreateMissingParentPath();
			sysUser = userAPI.getSystemUser();
			fileAssetContentType = getFileAssetContentType("FileAsset", sysUser);
			int migrated = 0;
			final List<Host> siteList = siteAPI.findAll(sysUser, false);
			if (null != siteList && !siteList.isEmpty()) {
				for (Host site : siteList) {
					Logger.info(this.getClass(),
							" \n**********************************************************************\n"
									+ "-> Migrating files for Site '" + site.getHostname() + "'");
					boolean skipMigration = Boolean.FALSE;
					if (Host.SYSTEM_HOST.equalsIgnoreCase(site.getIdentifier())) {
						Logger.info(this.getClass(),
								" \nNOTE: The new Files as Contents CANNOT LIVE UNDER SYSTEM_HOST. Therefore, these legacy files will be permanently deleted.");
						skipMigration = Boolean.TRUE;
					}
					Logger.info(this.getClass(), " \n");
					final boolean isLiveInode = Boolean.TRUE;
					final List<File> filesPerSite = fileAPI.getAllHostFiles(site, !isLiveInode, sysUser,
							!RESPECT_FRONTEND_ROLES);
					int counter = 1;
					for (File legacyFile : filesPerSite) {
						if (migrated == 0) {
							HibernateUtil.startTransaction();
						}
						Logger.info(this.getClass(), counter + ". Processing file: " + legacyFile.getURI());
						counter++;
						if (skipMigration) {
							deleteLegacyFile(legacyFile);
							migrated++;
						} else {
							if (migrateLegacyFile(legacyFile)) {
								migrated++;
							}
						}
						if (migrated == filesPerSite.size() || (migrated > 0 && migrated % 100 == 0)) {
							HibernateUtil.commitTransaction();
						}
					}
					Logger.info(this.getClass(),
							" \n \nAll Legacy files under site '" + site.getHostname() + "' have been processed.\n"
									+ "**********************************************************************\n");
				}
				Logger.info(this.getClass(),
						" \n" + "\n-> Total processed files = " + migrated + "\n \n"
								+ "All legacy files have been processed. Please undeploy the Legacy File Migrator plugin now.\n"
								+ " \n");
			} else {
				Logger.error(this.getClass(),
						" \nAn error occurred: No Sites could be retrieved. Have you tried re-indexing your contents first?\n");
			}
		} catch (Exception ex) {
			try {
				Logger.error(this.getClass(), "An error occurred when migrating Files to Contents: " + ex.getMessage(),
						ex);
				HibernateUtil.rollbackTransaction();
			} catch (DotHibernateException e1) {
				Logger.warn(this, e1.getMessage(), e1);
			}
		} finally {
			try {
				HibernateUtil.closeSession();
			} catch (DotHibernateException e) {
				Logger.error(this.getClass(), "An error occurred when closing the Hibernate session.", e);
			}
		}
	}

	/**
	 * Sets a valid parent path for legacy files whose parent path is null or
	 * empty, which can be caused by different circumstances. This is a data
	 * inconsistency and must be fixed before migrating such legacy files to
	 * files as content.
	 * 
	 * @throws DotDataException
	 *             An error occurred when updating the records in the data
	 *             source.
	 */
	private void recreateMissingParentPath() throws DotDataException {
		final DotConnect dc = new DotConnect();
		final String whereClause = "WHERE parent_path IS NULL OR parent_path = '' OR parent_path = ' ' AND asset_type = 'file_asset'";
		String query = "SELECT id FROM identifier " + whereClause;
		dc.setSQL(query);
		final List<Map<String, Object>> results = dc.loadObjectResults();
		if (!results.isEmpty()) {
			Logger.info(this.getClass(), " \n=== A total of " + results.size()
					+ " legacy files have an invalid parent path. Fixing data... ===");
			query = "UPDATE identifier SET parent_path = '/' " + whereClause;
			dc.setSQL(query);
			dc.loadResult();
			for (Map<String, Object> record : results) {
				final String identifier = record.get("id").toString();
				CacheLocator.getIdentifierCache().removeFromCacheByIdentifier(identifier);
			}
		}
	}

	/**
	 * Looks up the Content Type object associated to the velocity variable name
	 * for the File Asset Content Type.
	 * 
	 * @param varName
	 *            - The velocity variable name for the File Asset Content Type.
	 * @param user
	 *            - The user performing this action.
	 * @return The File Asset Content Type object.
	 * @throws DotSecurityException
	 *             The specified user does not have permissions to access the
	 *             File Asset Content Type.
	 * @throws DotDataException
	 *             An error occurred when interacting with the data source.
	 */
	private Structure getFileAssetContentType(final String varName, final User user)
			throws DotSecurityException, DotDataException {
		final Structure contentType = StructureFactory.getStructureByVelocityVarName(varName);
		if (!APILocator.getPermissionAPI().doesUserHavePermission(contentType, PermissionAPI.PERMISSION_READ, user)) {
			throw new DotSecurityException(
					"User [" + user.getUserId() + "] does not have permission to access Content Type " + varName);
		}
		return contentType;
	}

	/**
	 * Performs the migration of the legacy file to the new file as content and
	 * takes care of the cleanup process for the legacy data: Removing the
	 * legacy file form the file system, permission and cache cleanup, etc.
	 * 
	 * @param file
	 *            - The legacy file to migrate.
	 * @return Returns {@code true} if the process was successful.
	 * @throws Exception
	 *             An error occurred when migrating the specified Legacy File.
	 */
	public boolean migrateLegacyFile(final File file) throws Exception {
		// Retrieve asset and version information of the legacy file to create
		// the new content file
		final Identifier legacyIdentifier = identifierAPI.find(file);
		final VersionInfo versionInfo = versionableAPI.getVersionInfo(legacyIdentifier.getId());
		final List<Versionable> legacyFileVersions = findAllVersions(file);
		final java.io.File fileReferenceInFS = fileAPI.getAssetIOFile(file);
		if (null == fileReferenceInFS || !fileReferenceInFS.exists()) {
			Logger.warn(this,
					"\nLegacy File '" + legacyIdentifier.getPath() + "' has no associated binary file. Deleting...");
			deleteLegacyFile(file);
		} else {
			final File working = (File) versionableAPI.findWorkingVersion(legacyIdentifier, sysUser,
					!RESPECT_ANON_PERMISSIONS);
			final Contentlet cworking = migrateLegacyFileData(file);
			Contentlet clive = null;
			setHostFolderValues(cworking, legacyIdentifier);
			// Migrate and clear permissions on the legacy file
			if (versionInfo.getLiveInode() != null && !versionInfo.getLiveInode().equals(versionInfo.getWorkingInode())) {
				final File live = (File) versionableAPI.findLiveVersion(legacyIdentifier, sysUser,
						!RESPECT_ANON_PERMISSIONS);
				clive = migrateLegacyFileData(live);
				setHostFolderValues(clive, legacyIdentifier);
			}
			if (!permissionAPI.isInheritingPermissions(working)) {
				final boolean bitPermissions = Boolean.TRUE;
				final boolean onlyIndividualPermissions = Boolean.TRUE;
				final boolean forceLoadFromDB = Boolean.TRUE;
				permissionAPI.getPermissions(working, bitPermissions, onlyIndividualPermissions, forceLoadFromDB);
			}
			// Delete the legacy file and use its working Inode to create the new
			// one
			final String workingInode = working.getInode();
			fileAPI.delete(working, sysUser, !RESPECT_FRONTEND_ROLES);
			fileReferenceInFS.delete();
			Iterator<Versionable> it = legacyFileVersions.iterator();
			while (it.hasNext()) {
				Versionable version = it.next();
				if (workingInode.equals(version.getInode())) {
					// Delete all the legacy file versions EXCEPT FOR the working
					// Inode, as it is used in the new content file
					it.remove();
				}
			}
			HibernateUtil.getSession().clear();
			CacheLocator.getIdentifierCache().removeFromCacheByIdentifier(legacyIdentifier.getId());
			// Check in the new content file
			if (clive != null) {
				try {
					checkinLive(clive);
				} catch (DotContentletValidationException e) {
					Logger.warn(this, "\nLegacy File '" + legacyIdentifier.getPath()
							+ "' has invalid fields. Re-trying to check in without validation...");
					clive.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
					checkinLive(clive);
					Logger.warn(this, "Done.");
				}
			} else {
				try {
					checkinWorking(cworking, versionInfo);
				} catch (DotContentletValidationException e) {
					Logger.warn(this, "\nLegacy File '" + legacyIdentifier.getPath()
							+ "' has invalid fields. Re-trying to check in without validation...");
					cworking.setProperty(Contentlet.DONT_VALIDATE_ME, Boolean.TRUE);
					checkinWorking(cworking, versionInfo);
					Logger.warn(this, "Done.");
				}
			}
			// Finally, delete all legacy file versions, if any
			deleteAllVersions(legacyFileVersions);
		}
		return true;
	}

	/**
	 * Checks in the live version of the specified file as content.
	 * 
	 * @param clive
	 *            - The live version of the content.
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
	private void checkinLive(final Contentlet clive) throws DotContentletValidationException,
			DotContentletStateException, IllegalArgumentException, DotDataException, DotSecurityException {
		final Contentlet cclive = contAPI.checkin(clive, sysUser, !RESPECT_FRONTEND_ROLES);
		contAPI.publish(cclive, sysUser, !RESPECT_FRONTEND_ROLES);
	}

	/**
	 * Checks in the working version of the specified file as content. Its
	 * version info will determine whether the content will be published or not.
	 * 
	 * @param cworking
	 *            - The working version of the content.
	 * @param vInfo
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
	private void checkinWorking(final Contentlet cworking, final VersionInfo vInfo)
			throws DotContentletValidationException, DotContentletStateException, IllegalArgumentException,
			DotDataException, DotSecurityException {
		final Contentlet ccworking = contAPI.checkin(cworking, sysUser, !RESPECT_FRONTEND_ROLES);
		if (vInfo.getLiveInode() != null && vInfo.getLiveInode().equals(ccworking.getInode())) {
			contAPI.publish(ccworking, sysUser, !RESPECT_FRONTEND_ROLES);
		}
	}

	/**
	 * Copies the specified legacy file to a temporary location in the file
	 * system.
	 * 
	 * @param file
	 *            - The legacy file that will be copied.
	 * @return The new temporary location of the legacy file.
	 */
	private java.io.File copyFileToTempFolder(final File file) {
		final java.io.File tmp = new java.io.File(fileAPI.getRealAssetPathTmpBinary() + java.io.File.separator
				+ file.getModUser() + java.io.File.separator + System.currentTimeMillis() + java.io.File.separator
				+ file.getFileName());
		if (tmp.exists()) {
			tmp.delete();
		}
		try {
			final java.io.File originalFile = fileAPI.getAssetIOFile(file);
			if (originalFile.exists()) {
				FileUtil.copyFile(originalFile, tmp);
			}
		} catch (IOException e) {
			Logger.error(this.getClass(), "Error processing Stream", e);
		}
		return tmp;
	}

	/**
	 * Takes a legacy file and assigns its properties and binary file to the
	 * Contentlet that will represent it from now on.
	 * 
	 * @param file
	 *            - The legacy file to transform.
	 * @return The new File as Content.
	 * @throws Exception
	 *             An error occurred when setting some contentlet's properties.
	 */
	private Contentlet migrateLegacyFileData(final File file) throws Exception {
		final Contentlet fileAsContent = new Contentlet();
		fileAsContent.setStructureInode(fileAssetContentType.getInode());
		fileAsContent.setLanguageId(langAPI.getDefaultLanguage().getId());
		fileAsContent.setInode(file.getInode());
		fileAsContent.setIdentifier(file.getIdentifier());
		fileAsContent.setModUser(file.getModUser());
		fileAsContent.setModDate(file.getModDate());
		fileAsContent.setStringProperty("title", file.getFileName());
		fileAsContent.setStringProperty("fileName", file.getFileName());
		fileAsContent.setStringProperty("description", file.getTitle());
		final java.io.File tmp = copyFileToTempFolder(file);
		fileAsContent.setBinary("fileAsset", tmp);
		return fileAsContent;
	}

	/**
	 * Assigns the respective Site and parent folder values to the new File as
	 * Content.
	 * 
	 * @param con
	 *            - The file as a contentlet.
	 * @param legacyIdentifier
	 *            - The Identifier pointing to the legacy file.
	 * @throws Exception
	 *             An error occurred when setting the parent folder.
	 */
	private void setHostFolderValues(final Contentlet con, final Identifier legacyIdentifier) throws Exception {
		con.setHost(legacyIdentifier.getHostId());
		con.setFolder(APILocator.getFolderAPI().findFolderByPath(legacyIdentifier.getParentPath(),
				legacyIdentifier.getHostId(), sysUser, !RESPECT_FRONTEND_ROLES).getInode());
	}

	/**
	 * Deletes the specified Legacy File along with all of its versions.
	 * 
	 * @param file
	 *            - The Legacy File.
	 * @throws Exception
	 *             An error occurred when deleting the data.
	 */
	private void deleteLegacyFile(final File file) throws Exception {
		final File working = (File) versionableAPI.findWorkingVersion(file.getIdentifier(), sysUser,
				!RESPECT_ANON_PERMISSIONS);
		final List<Versionable> legacyFileVersions = findAllVersions(file);
		deleteAllVersions(legacyFileVersions);
		fileAPI.delete(working, sysUser, !RESPECT_FRONTEND_ROLES);
	}

	/**
	 * Returns all the different versions of the Legacy File.
	 * 
	 * @param file
	 *            - The Legacy File.
	 * @return The list of Legacy File versions.
	 * @throws DotStateException
	 *             An error occurred when retrieving the information.
	 * @throws DotDataException
	 *             An error occurred when accessing the data source.
	 * @throws DotSecurityException
	 *             The specified user does not have permissions to perform this
	 *             action.
	 */
	private List<Versionable> findAllVersions(final File file)
			throws DotStateException, DotDataException, DotSecurityException {
		final Identifier legacyIdentifier = identifierAPI.find(file);
		return versionableAPI.findAllVersions(legacyIdentifier, sysUser, !RESPECT_FRONTEND_ROLES);
	}

	/**
	 * Deletes the specified list of Legacy File versions from the file system.
	 * 
	 * @param legacyFileVersions
	 *            - The list of Legacy File versions.
	 * @throws IOException
	 *             An error occurred when deleting the file form the file
	 *             system.
	 */
	private void deleteAllVersions(final List<Versionable> legacyFileVersions) throws IOException {
		if (null != legacyFileVersions) {
			for (Versionable versionable : legacyFileVersions) {
				final File legacyFile = (File) versionable;
				final java.io.File fileReference = fileAPI.getAssetIOFile(legacyFile);
				if (null != fileReference && fileReference.exists()) {
					deleteFilesInFileSystem(fileReference);
				}
			}
		}
	}

	/**
	 * Removes the original legacy file and the versions that were created via
	 * the WYSIWYG, the Image Editor, or the Image Servlet. For example, in the
	 * {@code /assets/} folder, a legacy file called
	 * {@code 2cb2a266-1784-4d12-8738-b59fd85910d1.jpg} can have the following
	 * versions:
	 * 
	 * <pre>
	 * 2cb2a266-1784-4d12-8738-b59fd85910d1_resized_250_w_974.jpg
	 * 2cb2a266-1784-4d12-8738-b59fd85910d1_thumb_100_100_255_255_255.jpg
	 * dotGenerated_2cb2a266-1784-4d12-8738-b59fd85910d1250_w_974.jpg
	 * </pre>
	 * 
	 * The original file name will be taken as a filter to find the other
	 * "edited" versions and the parent folder where they are located.
	 * 
	 * @param fileReference
	 *            - The reference to the original legacy file.
	 */
	private void deleteFilesInFileSystem(final java.io.File fileReference) {
		final String legacyFileName = fileReference.getName();
		final java.io.File parentDir = fileReference.getParentFile();
		if (parentDir.isDirectory()) {
			for (java.io.File file : parentDir.listFiles()) {
				if (!file.isDirectory() && file.getName().contains(FilenameUtils.removeExtension(legacyFileName))) {
					file.delete();
				}
			}
		}
	}

}
