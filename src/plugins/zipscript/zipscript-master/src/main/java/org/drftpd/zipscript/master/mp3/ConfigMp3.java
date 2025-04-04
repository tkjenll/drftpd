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
package org.drftpd.zipscript.master.mp3;

import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.zipscript.common.flac.FlacInfo;
import org.drftpd.zipscript.common.mp3.MP3Info;

public class ConfigMp3 extends ConfigElement<MP3Info> {

    @SuppressWarnings("unused")
    public ConfigMp3() {
        super();
    }

    public ConfigMp3(MP3Info value) {
        super(value);
    }
}
