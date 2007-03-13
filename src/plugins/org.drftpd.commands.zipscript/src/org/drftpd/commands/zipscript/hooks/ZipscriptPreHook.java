/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands.zipscript.hooks;

import java.io.FileNotFoundException;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.SFVInfo;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PreHookInterface;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.config.FtpConfig;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptPreHook implements PreHookInterface {

	private static final Logger logger = Logger.getLogger(ZipscriptPreHook.class);

	private boolean _sfvFirstRequired;

	private boolean _sfvFirstAllowNoExt;

	public void initialize() {
		// SFV First PathPermissions
		String sfvFirstUsers = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf").getProperty("sfvfirst.users");
		_sfvFirstRequired = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf").getProperty("sfvfirst.required").equals("true");
		_sfvFirstAllowNoExt = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf").getProperty("sfvfirst.allownoext").equals("true");
		if (_sfvFirstRequired) {
			try {
				// this one gets perms defined in sfvfirst.users
				StringTokenizer st = new StringTokenizer(GlobalContext.getGlobalContext()
						.getPluginsConfig().getPropertiesForPlugin("zipscript.conf")
						.getProperty("sfvfirst.pathcheck"));
				while (st.hasMoreTokens()) {
					GlobalContext.getGlobalContext().getConfig().addPathPermission(
							"sfvfirst.pathcheck",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer(
											sfvFirstUsers, " "))));
				}
				st = new StringTokenizer(GlobalContext.getGlobalContext()
						.getPluginsConfig().getPropertiesForPlugin("zipscript.conf")
						.getProperty("sfvfirst.pathignore"));
				while (st.hasMoreTokens()) {
					GlobalContext.getGlobalContext().getConfig().addPathPermission(
							"sfvfirst.pathignore",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer("*", " "))));
				}
			} catch (MalformedPatternException e) {
				logger.warn("Exception when reading conf/plugins/zipscript.conf", e);
			}
		}
	}

	public CommandRequestInterface doZipscriptSTORPreCheck(CommandRequest request) {

		if (!request.hasArgument()) {
			// Syntax error but we'll let the command itself deal with it
			return request;
		}
		String checkName = request.getArgument().toLowerCase();
		// Read config
		boolean multiSfvAllowed = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf").getProperty("allow.multi.sfv").equals("true");
		boolean restrictSfvEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf").getProperty("sfv.restrict.files").equals("true");
		boolean sfvFirstEnforcedPath = checkSfvFirstEnforcedPath(request.getCurrentDirectory(), 
				request.getSession().getUserNull(request.getUser()));
		logger.debug("sfv first: "+sfvFirstEnforcedPath);
		try {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
			SFVInfo sfv = sfvData.getSFVInfo();
			if (checkName.endsWith(".sfv") && !multiSfvAllowed) {
				request.setAllowed(false);
				request.setDeniedResponse(new CommandResponse(533,
					"Requested action not taken. Multiple SFV files not allowed."));
			}
			else if (sfvFirstEnforcedPath && !checkAllowedExtension(checkName)) {
				// filename not explicitly permitted, check for sfv entry
				boolean allow = false;
				if (restrictSfvEnabled) {
					for (String name : sfv.getEntries().keySet()) {
						if (name.toLowerCase().equals(checkName)) {
							allow = true;
							break;
						}
					}
					if (!allow) {
						request.setAllowed(false);
						request.setDeniedResponse(new CommandResponse(533,
							"Requested action not taken. File not found in sfv."));
					}
				}
			}
			else {
				return request;
			}
		} catch (FileNotFoundException e1) {
			// no sfv found in dir 
			if ( !checkAllowedExtension(checkName) && 
					sfvFirstEnforcedPath ) {
				// filename not explicitly permitted
				// ForceSfvFirst is on, and file is in an enforced path.
				request.setAllowed(false);
				request.setDeniedResponse(new CommandResponse(533,
				"Requested action not taken. You must upload sfv first."));
			}
		} catch (NoAvailableSlaveException e1) {
			//sfv not online, do nothing
		}
		return request;
	}

	private boolean checkAllowedExtension(String file) {
		if (_sfvFirstAllowNoExt && !file.contains(".")) {
			return true;
		}
		StringTokenizer st = new StringTokenizer(GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("zipscript.conf").getProperty("allowedexts"));
		while (st.hasMoreElements()) {
			String ext = "." + st.nextElement().toString().toLowerCase();
			if (file.toLowerCase().endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	private boolean checkSfvFirstEnforcedPath(DirectoryHandle dir, User user) {
		if (_sfvFirstRequired
				&& GlobalContext.getGlobalContext().getConfig().checkPathPermission("sfvfirst.pathcheck",
						user, dir)
				&& !GlobalContext.getGlobalContext().getConfig().checkPathPermission(
						"sfvfirst.pathignore", user, dir)) {
			return true;
		}
		return false;
	}
}
