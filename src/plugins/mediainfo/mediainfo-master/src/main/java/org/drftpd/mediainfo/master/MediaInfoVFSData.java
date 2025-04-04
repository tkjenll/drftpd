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
package org.drftpd.mediainfo.master;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.mediainfo.common.AsyncResponseMediaInfo;
import org.drftpd.mediainfo.common.MediaInfo;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author scitz0
 */
public class MediaInfoVFSData {
    private final FileHandle _file;

    public MediaInfoVFSData(FileHandle file) {
        _file = file;
    }

    public MediaInfo getMediaInfo() throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        try {
            MediaInfo mediaInfo = getMediaInfoFromInode(_file);
            try {
                if (_file.exists()) {
                    if (_file.getCheckSum() == mediaInfo.getChecksum()) {
                        // 	passed all tests
                        return mediaInfo;
                    }
                }
            } catch (FileNotFoundException e) {
                // just continue, it couldn't find the previous media file, the line below here will remove it
                // we will then continue to try to find a new one right afterward
            }
            _file.removePluginMetaData(MediaInfo.MEDIAINFO);
        } catch (KeyNotFoundException e1) {
            // bah, let's load it
        }

        for (int i = 0; i < 5; i++) {
            MediaInfo info;
            RemoteSlave rslave = _file.getASlaveForFunction();
            String index;
            try {
                index = getMediaInfoIssuer().issueMediaFileToSlave(rslave, _file.getPath());
                info = fetchMediaInfoFromIndex(rslave, index);
            } catch (SlaveUnavailableException e) {
                // okay, it went offline while trying, continue
                continue;
            } catch (RemoteIOException e) {
                throw new IOException(e.getMessage());
            }
            if (info == null) {
                throw new IOException("Failed to run mediainfo on media file");
            }
            info.setChecksum(_file.getCheckSum());
            info.setFileName(_file.getName());
            _file.addPluginMetaData(MediaInfo.MEDIAINFO, new ConfigMediaInfo(info));
            return info;
        }
        throw new SlaveUnavailableException("No slave for media file available");
    }

    private MediaInfo getMediaInfoFromInode(FileHandle vfsFileHandle) throws FileNotFoundException, KeyNotFoundException {
        return vfsFileHandle.getPluginMetaData(MediaInfo.MEDIAINFO);
    }

    private MediaInfo fetchMediaInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
        return ((AsyncResponseMediaInfo) rslave.fetchResponse(index)).getMediaInfo();
    }

    private MediaInfoIssuer getMediaInfoIssuer() {
        return (MediaInfoIssuer) GlobalContext.getGlobalContext().getSlaveManager().
                getProtocolCentral().getIssuerForClass(MediaInfoIssuer.class);
    }
}
