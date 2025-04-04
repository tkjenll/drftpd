/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.commands.nuke;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.beans.XMLDecoder;
import java.io.*;
import java.util.*;

import static org.drftpd.master.util.SerializerUtils.getMapper;

/**
 * NukeBeans handles the logging of nukes. Using a TreeMap, it sorts all nukes
 * in alphabetical order using the path. To save/load the current nukelog, we
 * are using JavaBeans XMLEncoder/XMLDecoder.
 *
 * @author fr0w
 * @version $Id$
 */
public class NukeBeans {

    protected static final Logger logger = LogManager.getLogger(NukeBeans.class);
    private static final String _nukebeansPath = "userdata";
    private static NukeBeans _nukeBeans = null;
    private Map<String, NukeData> _nukes = null;

    /**
     * Singleton.
     */
    private NukeBeans() {
    }

    /**
     * This method iterate through the Map of the users which have been nuked on
     * the NukeData.getPath(), and create a List<Nukee> Object. See:
     * net.sf.drftpd.Nukee for more info.
     *
     * @param nd
     * @return
     */
    public static List<NukedUser> getNukeeList(NukeData nd) {
        ArrayList<NukedUser> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : nd.getNukees().entrySet()) {
            String user = entry.getKey();
            Long l = entry.getValue();
            list.add(new NukedUser(user, l));
        }
        return list;
    }

    /**
     * Singleton method.
     *
     * @return NukeBeans.
     */
    public static NukeBeans getNukeBeans() {
        if (_nukeBeans == null) {
            logger.debug("Instantiating NukeBeans.");
            newInstance();
        }

        return _nukeBeans;
    }

    /**
     * Creates a new instance of NukeBeans. De-serialize the .json/xml.
     */

    public static void newInstance() {
        _nukeBeans = new NukeBeans();
        _nukeBeans.loadLRUMap();
    }

    /**
     * Testing purposes.
     *
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("DEBUG: Testing NukeBeans");

        for (int i = 0; i < 100; i++) {
            // settings...
            String user = "test" + i;
            String path = "/APPS/A" + i;
            long size = Bytes.parseBytes("350MB");
            int multiplier = 3;
            long nukedAmount = multiplier * size;
            String reason = "Testing";
            Map<String, Long> nukees = new Hashtable<>();
            nukees.put("test" + i, nukedAmount);

            // actual NukeEvent
            NukeData nd = new NukeData();
            nd.setUser(user);
            nd.setPath(path);
            nd.setReason(reason);
            nd.setNukees(nukees);
            nd.setMultiplier(multiplier);
            nd.setAmount(nukedAmount);
            nd.setSize(size);

            // System.out.println(nd.toString());
            // adding
            getNukeBeans().add(nd);
        }

        // commiting.
        try {
            getNukeBeans().commit();
        } catch (IOException e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        // finished!
        System.out.println("DEBUG: Test ran successfully");
    }

    /**
     * Get the NukeData Object of the given path.
     *
     * @param path
     * @throws ObjectNotFoundException, if not object is found.
     */
    public synchronized NukeData get(String path)
            throws ObjectNotFoundException {
        NukeData ne = _nukes.get(path);
        if (ne == null)
            throw new ObjectNotFoundException("No nukelog for: " + path);
        return ne;
    }

    /**
     * See add(String, NukeData).
     *
     * @param nd
     */
    public void add(NukeData nd) {
        add(nd.getPath(), nd);
    }

    /**
     * Adds the given NukeData Object to the TreeMap and then serializes the
     * TreeMap.
     *
     * @param path
     * @param nd
     */
    public synchronized void add(String path, NukeData nd) {
        _nukes.put(path, nd);
        try {
            commit();
        } catch (IOException e) {
            logger.error("Couldn't save the nukelog due to: {}", e.getMessage(), e);
        }
    }

    /**
     * This method will try to remove the given path from the nukelog.
     *
     * @param path
     * @throws ObjectNotFoundException, if this path is not on the nukelog.
     */
    public synchronized void remove(String path) throws ObjectNotFoundException {
        NukeData ne = _nukes.remove(path);
        if (ne == null)
            throw new ObjectNotFoundException("No nukelog for: " + path);
        try {
            commit();
        } catch (IOException e) {
            logger.error("Couldn't save the nukelog deu to: {}", e.getMessage(), e);
        }
    }

    /**
     * @return all NukeData Objects stored on the TreeMap.
     */
    public synchronized Collection<NukeData> getAll() {
        return _nukes.values();
    }

    /**
     * @return nukes map.
     */
    public synchronized Map<String, NukeData> getNukes() {
        return _nukes;
    }

    /**
     * @param path
     * @return true if the given path is on the nukelog or false if it isn't.
     */
    public synchronized NukeData findPath(String path) {
        try {
            return get(path);
        } catch (ObjectNotFoundException e) {
            return null;
        }
    }

    /**
     * @param name
     * @return true if the given name is in the nukelog or false if it isn't.
     */
    public synchronized NukeData findName(String name) {
        for (NukeData nd : getAll()) {
            if (VirtualFileSystem.getLast(nd.getPath()).equals(name)) {
                return nd;
            }
        }
        return null;
    }

    /**
     * Serializes the TreeMap.
     *
     * @throws IOException
     */
    public void commit() throws IOException {
        try {
            File nukeFile = new File(_nukebeansPath + VirtualFileSystem.separator + "nukebeans.json");
            getMapper().writeValue(nukeFile, this);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * @param nukes
     */
    public void setLRUMap(Map<String, NukeData> nukes) {
        _nukes = nukes;
    }

    // TODO @k2r Save class loader?

    /**
     * Deserializes the Nukelog Map.
     */
    @SuppressWarnings("unchecked")
    private void loadLRUMap() {
        Map<String, NukeData> nukees = new LRUMap<>(200);
        try {
            FileReader fileReader = new FileReader(_nukebeansPath + VirtualFileSystem.separator + "nukebeans.json");
            Map<String, NukeData> nukes = getMapper().readValue(fileReader, new TypeReference<>() {});
            nukees.putAll(nukes);
        } catch (FileNotFoundException e) {
            loadXMLLRUMap(nukees);
        } catch (Exception e) {
            logger.error("IOException reading json nuke log file", e);
        }
        _nukeBeans.setLRUMap(nukees);
    }

    /**
     * Legacy XML loader
     * Deserializes the Nukelog Map.
     */
    @SuppressWarnings("unchecked")
    private void loadXMLLRUMap(Map<String, NukeData> nukees) {
        // de-serializing the Hashtable.
        try (XMLDecoder xd = new XMLDecoder(new FileInputStream(
                _nukebeansPath + VirtualFileSystem.separator + "nukebeans.xml"))) {
            //switchClassLoaders();
            nukees.putAll((Map<String, NukeData>) xd.readObject());
            logger.debug("Loaded log from .xml, size: {}", nukees.size());
        } catch (FileNotFoundException e) {
            // nukelog does not exists yet.
        }
    }
}
