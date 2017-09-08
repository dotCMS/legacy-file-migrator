package com.dotmarketing.osgi.override;

import com.dotcms.repackage.org.osgi.framework.BundleContext;
import com.dotmarketing.osgi.GenericBundleActivator;
import com.dotmarketing.osgi.util.LegacyFilesMigrator;
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
 * @version 3.3
 * @since Aug 30th, 2017
 */
public class Activator extends GenericBundleActivator {

	private LoggerContext pluginLoggerContext;

	@Override
	public void start(BundleContext context) throws Exception {
		//Initializing log4j...
        LoggerContext dotcmsLoggerContext = Log4jUtil.getLoggerContext();
        //Initialing the log4j context of this plugin based on the dotCMS logger context
        pluginLoggerContext = (LoggerContext) LogManager.getContext(this.getClass().getClassLoader(),
                false,
                dotcmsLoggerContext,
                dotcmsLoggerContext.getConfigLocation());
        
		// Initializing services...
		initializeServices(context);
		// Expose bundle elements
		publishBundleServices(context);
		final LegacyFilesMigrator migrator = new LegacyFilesMigrator();
		Thread migrationThread = new Thread(new Runnable() {

			@Override
			public void run() {
				migrator.migrateLegacyFiles();
			}

		});
		migrationThread.start();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// Unpublish bundle services
		unpublishBundleServices();
		//Shutting down log4j in order to avoid memory leaks
        Log4jUtil.shutdown(pluginLoggerContext);
	}

}
