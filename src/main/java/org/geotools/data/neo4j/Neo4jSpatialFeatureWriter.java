/**
 * Copyright (c) 2010-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 * This file is part of Neo4j Spatial.
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.geotools.data.neo4j;

import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.neo4j.gis.spatial.EditableLayer;
import org.neo4j.graphdb.Transaction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * FeatureWriter implementation. Instances of this class are created by
 * Neo4jSpatialDataStore.
 *
 * @author Davide Savazzi, Andreas Wilhelm
 */
public class Neo4jSpatialFeatureWriter implements
    FeatureWriter<SimpleFeatureType, SimpleFeature> {
    // current for FeatureWriter
    private SimpleFeature live;
    // copy of live returned to user
    private SimpleFeature current;
    private SimpleFeatureType featureType;
    private FeatureReader<SimpleFeatureType, SimpleFeature> reader;
    private EditableLayer layer;
    private boolean closed;
    private static final Logger LOGGER = org.geotools.util.logging.Logging
        .getLogger("org.neo4j.gis.spatial");

    /**
     * @param layer
     * @param reader
     */
    protected Neo4jSpatialFeatureWriter(EditableLayer layer,
                                        FeatureReader<SimpleFeatureType, SimpleFeature> reader) {
        this.reader = reader;
        this.layer = layer;
        this.featureType = reader.getFeatureType();
    }

    /**
     *
     */
    public SimpleFeatureType getFeatureType() {
        return featureType;
    }

    /**
     *
     */
    public boolean hasNext() throws IOException {
        if (closed) {
            throw new IOException("Feature writer is closed");
        }
        return reader != null && reader.hasNext();
    }

    /**
     *
     */
    public SimpleFeature next() throws IOException {
        if (closed) {
            throw new IOException("FeatureWriter has been closed");
        }

        SimpleFeatureType featureType = getFeatureType();

        if (hasNext()) {
            live = reader.next();
            current = SimpleFeatureBuilder.copy(live);
            LOGGER.finer("Calling next on writer");
        } else {
            // new content
            live = null;
            current = SimpleFeatureBuilder.template(featureType, null);
        }

        return current;
    }

    /**
     *
     */
    public void remove() throws IOException {
        if (closed) {
            throw new IOException("FeatureWriter has been closed");
        }

        if (current == null) {
            throw new IOException("No feature available to remove");
        }

        if (live != null) {
            LOGGER.fine("Removing " + live);

            try (Transaction tx = layer.getSpatialDatabase().getDatabase().beginTx()) {
                layer.delete(Long.parseLong(live.getID()));
                tx.success();
            }
        }

        live = null;
        current = null;
    }

    /**
     *
     */
    public void write() throws IOException {
        if (closed) {
            throw new IOException("FeatureWriter has been closed");
        }

        if (current == null) {
            throw new IOException("No feature available to write");
        }

        LOGGER.fine("Write called, live is " + live + " and cur is " + current);

        if (live != null) {
            if (!live.equals(current)) {
                LOGGER.fine("Updating " + current);
                try (Transaction tx = layer.getSpatialDatabase().getDatabase().beginTx()) {
                    layer.update(Long.parseLong(current.getID()),
                        (Geometry) current.getDefaultGeometry());
                    tx.success();
                }
            }
        } else {
            LOGGER.fine("Inserting " + current);
            try (Transaction tx = layer.getSpatialDatabase().getDatabase().beginTx()) {
                layer.add((Geometry) current.getDefaultGeometry());
                tx.success();
            }
        }

        live = null;
        current = null;
    }

    /**
     *
     */
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
        closed = true;
    }
}
