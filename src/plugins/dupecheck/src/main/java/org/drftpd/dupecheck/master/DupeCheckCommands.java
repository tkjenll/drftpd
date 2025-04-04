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
package org.drftpd.dupecheck.master;

import org.drftpd.common.dynamicdata.element.ConfigBoolean;
import org.drftpd.dupecheck.master.metadata.DupeCheckFileData;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.IndexEngineInterface;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.StringTokenizer;

public class DupeCheckCommands extends CommandInterface {

    /*
     * Site UNDUPE command to un-dupe specific files/folders
     *
     * This uses lucene's search to figure out if the file exist,
     * and then adds metadata to it so site knows its unduped.
     */
    public CommandResponse doSITE_UNDUPE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String arg = st.nextToken();
        if (arg.length() < 1) {
            throw new ImproperUsageException();
        }

        CommandResponse response = new CommandResponse(200, "Un-Dupe Complete");

        AdvancedSearchParams params = new AdvancedSearchParams();
        params.setExact(arg);

        try {
            IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
            Map<String, String> inodes = ie.advancedFind(GlobalContext.getGlobalContext().getRoot(), params, "doSITE_UNDUPE");

            if (!inodes.isEmpty()) {
                for (Map.Entry<String, String> item : inodes.entrySet()) {
                    InodeHandle inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey()) : new FileHandle(item.getKey());
                    try {
                        inode.addPluginMetaData(DupeCheckFileData.DUPE, new ConfigBoolean(false));
                        response.addComment("Unduped: " + inode.getPath());
                    } catch (FileNotFoundException e) {
                        // File Not Found - Deleted?? Probably not a good thing
                    }
                }
            }
        } catch (IndexException e) {
            //Index Exception while searching
            response.addComment(e);
        }

        return response;
    }
}