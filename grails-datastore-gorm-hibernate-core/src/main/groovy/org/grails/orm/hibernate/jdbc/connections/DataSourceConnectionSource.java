package org.grails.orm.hibernate.jdbc.connections;

import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * A {@link org.grails.datastore.mapping.core.connections.ConnectionSource} for JDBC {@link DataSource} objects. Attempts to close the pool if a "close" method is provided.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class DataSourceConnectionSource extends DefaultConnectionSource<DataSource, DataSourceSettings> {
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceConnectionSource.class);

    public DataSourceConnectionSource(String name, DataSource source, DataSourceSettings settings) {
        super(name, source, settings);
    }

    @Override
    public void close() throws IOException {
        super.close();
        if(!closed) {
            Method closeMethod = ReflectionUtils.findMethod(getSource().getClass(), "close");
            if(closeMethod != null) {
                try {
                    ReflectionUtils.invokeMethod(closeMethod, getSource());
                    this.closed = true;
                } catch (Throwable e) {
                    LOG.warn("Error closing JDBC connection [{}]: {}", getName(), e.getMessage());
                }
            }
        }
    }
}