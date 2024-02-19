package test;

import java.io.File;
import java.io.FileFilter;

public class FileFilterExtension  implements FileFilter{
	@Override
	public boolean accept(File pathname) {
		if (pathname.getName().endsWith(".jpg")) {
			return true;
		}
		return false;
	}
}
