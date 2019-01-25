package com.dotmarketing.osgi.migrator;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.dotcms.repackage.org.apache.commons.lang.StringUtils;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.files.business.FileAPI;
import com.dotmarketing.portlets.files.model.File;
import com.dotmarketing.util.Logger;

/**
 * Provides different utility methods used by the classes in the Migration
 * Plugin.
 * 
 * @author Jose Castro
 * @version 3.7.1
 * @since Oct 9, 2018
 *
 */
public class MigratorUtils {

	private static final String PROPERTY_FILE_NAME = "plugin.properties";
	private static final String LOAD_QUERY_PROP = "legacyfile.migrator.loadquery";
	private static final String BATCH_SIZE_PROP = "legacyfile.migrator.batchsize";
	private static final String STATUS_PROP = "legacyfile.migrator.status";
	private static final String LOGGING_FREQUENCY_PROP = "legacyfile.migrator.loggingfrequency";

	private static final String DEFAULT_BATCH_SIZE_PROP = "50";
	private static final String DEFAULT_STATUS_PROP = "1";
	private static final String DEFAULT_LOGGING_FREQUENCY_PROP = "10";

	/**
	 * Reads the plugin's configuration parameters located inside the
	 * <code>/src/main/resources/plugin.properties</code> file.
	 * 
	 * @return The {@link Configuration} containing the configuration parameters for
	 *         this plugin.
	 */
	public static Configuration initPluginConfig() {
		final Configuration configuration = new Configuration();
		final Properties properties = new Properties();
		try {
			final InputStream in = MigratorUtils.class.getResourceAsStream("/" + PROPERTY_FILE_NAME);
			properties.load(in);
			configuration.setLoadQuery(properties.getProperty(LOAD_QUERY_PROP, StringUtils.EMPTY));
			configuration
					.setBatchSize(Integer.parseInt(properties.getProperty(BATCH_SIZE_PROP, DEFAULT_BATCH_SIZE_PROP)));
			configuration.setStatus(Integer.parseInt(properties.getProperty(STATUS_PROP, DEFAULT_STATUS_PROP)));
			configuration.setLoggingFrequency(
					Integer.parseInt(properties.getProperty(LOGGING_FREQUENCY_PROP, DEFAULT_LOGGING_FREQUENCY_PROP)));
		} catch (final FileNotFoundException e) {
			Logger.warn(MigratorUtils.class, "Properties file " + PROPERTY_FILE_NAME + " not found.", e);
		} catch (final IOException e) {
			Logger.error(MigratorUtils.class, "Can't read properties file " + PROPERTY_FILE_NAME, e);
		}
		return configuration;
	}

	/**
	 * Converts a millisecond duration to a String format.
	 * 
	 * @param millis
	 *            - A duration to convert to a string form
	 * @return A String of the form "X Days Y Hours Z Minutes A Seconds".
	 */
	public static String getDurationBreakdown(long millis) {
		final long days = TimeUnit.MILLISECONDS.toDays(millis);
		millis -= TimeUnit.DAYS.toMillis(days);
		final long hours = TimeUnit.MILLISECONDS.toHours(millis);
		millis -= TimeUnit.HOURS.toMillis(hours);
		final long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
		millis -= TimeUnit.MINUTES.toMillis(minutes);
		final long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
		final StringBuilder sb = new StringBuilder();
		sb.append(days).append(" Days ");
		sb.append(hours).append(" Hours ");
		sb.append(minutes).append(" Minutes ");
		sb.append(seconds).append(" Seconds");
		return sb.toString();
	}

	/**
	 * Copies the specified legacy file to a temporary location in the file system.
	 * 
	 * @param file
	 *            - The legacy file that will be copied.
	 * @return The new temporary location of the legacy file.
	 */
	public static java.io.File getBinaryFileFromLegacyFile(final File file) {
		final FileAPI fileAPI = APILocator.getFileAPI();
		try {
			final java.io.File originalFile = fileAPI.getAssetIOFile(file);
			if (originalFile.exists()) {
				return originalFile;
			}
		} catch (final IOException e) {
			Logger.error(MigratorUtils.class, "An error occurred when retrieving file '" + file.getFileName() + "'", e);
		}
		return null;
	}

	/**
	 * Checks if the Binary File associated to the specified Contentlet exists or
	 * not. Files with size zero will be considered as missing.
	 * 
	 * @param contentlet
	 *            - The Contentlet whose Binary File will be checked.
	 * @return Returns <code>true</code> if the file is present. Otherwise, returns
	 *         <code>false</code>.
	 */
	public static boolean isBinaryFilePresent(final Contentlet contentlet) {
		boolean isPresent = Boolean.FALSE;
		try {
			final java.io.File binaryFile = contentlet.getBinary("fileAsset");
			if (null != binaryFile && binaryFile.exists() && binaryFile.length() > 0) {
				isPresent = Boolean.TRUE;
			}
		} catch (final IOException e) {
			Logger.error(MigratorUtils.class,
					"An error occurred when reading binary file from Contentlet '" + contentlet.getIdentifier() + "'",
					e);
		}
		return isPresent;
	}

}
