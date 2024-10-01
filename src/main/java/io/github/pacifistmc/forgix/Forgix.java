package io.github.pacifistmc.forgix;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

public class Forgix {
	public static final String MANIFEST_VERSION_KEY = "Forgix-Version";
	public static final String version = "1.2.10";
	public static Set<PosixFilePermission> perms = EnumSet.of(OTHERS_EXECUTE, OTHERS_WRITE, OTHERS_READ,
			OWNER_EXECUTE, OWNER_WRITE, OWNER_READ,
			GROUP_EXECUTE, GROUP_WRITE, GROUP_READ);
}
