package com.e_gineering.maven.gitflowhelper;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;

/**
 * Stupid adapter from Plexus Logger to Maven Log.
 * Needed since we're using ScmUtils from both mojo's and the extension.
 * Those have different loggers.
 */
public class PlexusLoggerToMavenLog implements Log {

    private final Logger plexusLog;

    public PlexusLoggerToMavenLog(Logger plexusLog) {
        this.plexusLog = plexusLog;
    }

    @Override
    public boolean isDebugEnabled() {
        return plexusLog.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence content) {
        plexusLog.debug(content.toString());
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        plexusLog.debug(content.toString(), error);
    }

    @Override
    public void debug(Throwable error) {
        plexusLog.debug("Error", error);
    }

    @Override
    public boolean isInfoEnabled() {
        return plexusLog.isInfoEnabled();
    }

    @Override
    public void info(CharSequence content) {
        plexusLog.info(content.toString());
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        plexusLog.info(content.toString(), error);
    }

    @Override
    public void info(Throwable error) {
        plexusLog.info("Error", error);
    }

    @Override
    public boolean isWarnEnabled() {
        return plexusLog.isWarnEnabled();
    }

    @Override
    public void warn(CharSequence content) {
        plexusLog.warn(content.toString());
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        plexusLog.warn(content.toString(), error);
    }

    @Override
    public void warn(Throwable error) {
        plexusLog.warn("Error", error);
    }

    @Override
    public boolean isErrorEnabled() {
        return plexusLog.isErrorEnabled();
    }

    @Override
    public void error(CharSequence content) {
        plexusLog.error(content.toString());
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        plexusLog.error(content.toString(), error);
    }

    @Override
    public void error(Throwable error) {
        plexusLog.error("Error", error);
    }

}
