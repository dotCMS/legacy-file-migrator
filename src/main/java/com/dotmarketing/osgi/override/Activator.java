package com.dotmarketing.osgi.override;

import com.dotcms.repackage.org.osgi.framework.BundleContext;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.osgi.migrator.LegacyFilesMigrator;
import com.dotcms.repackage.org.apache.logging.log4j.LogManager;
import com.dotcms.repackage.org.apache.logging.log4j.core.LoggerContext;
import com.dotmarketing.loggers.Log4jUtil;

/**
 * Activator class for the Legacy File Migration plugin. The approach for the
 * migration process is to spawn a new thread that will be in charge of
 * migrating the legacy files and, therefore, keep the OSGi framework from
 * having to extend the start phase of the bundle because of such a heavy
 * process.
 * 
 * @author Jose Orsini, Jose Castro
 * @version 3.3, 3.7.1
 * @since Aug 30th, 2017
 */
public class Activator extends GenericBundleActivator {

	private LoggerContext pluginLoggerContext;
	private LegacyFilesMigrator migrator;

	@Override
	public void start(final BundleContext context) throws Exception {
		// Initializing Log4j
        final LoggerContext dotcmsLoggerContext = Log4jUtil.getLoggerContext();
        // Initializing the log4j context of this plugin based on the dotCMS logger context
        this.pluginLoggerContext = (LoggerContext) LogManager.getContext(this.getClass().getClassLoader(),
                false,
                dotcmsLoggerContext,
                dotcmsLoggerContext.getConfigLocation());
        
		// Initializing services...
		initializeServices(context);
		// Expose bundle elements
		publishBundleServices(context);
		this.migrator = new LegacyFilesMigrator();
		Thread migrationThread = new Thread(new Runnable() {

			@Override
			public void run() {
				migrator.startMigration();
			}

		});
		migrationThread.start();
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		// Unpublish bundle services
		unpublishBundleServices();
		this.migrator.stopMigration();
		// Shutting down log4j in order to avoid memory leaks
        Log4jUtil.shutdown(this.pluginLoggerContext);
	}

}
