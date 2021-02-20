package org.araqnid.eventstore.testutil;

import org.junit.Rule;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NIOTemporaryFolder extends ExternalResource {
	private final Path parentFolder;
	private Path folder;

	public NIOTemporaryFolder() {
		this(Paths.get(System.getProperty("java.io.tmpdir")));
	}

	public NIOTemporaryFolder(Path parentFolder) {
		this.parentFolder = parentFolder;
	}

	@Override
	protected void before() throws Throwable {
		create();
	}

	@Override
	protected void after() {
		delete();
	}

	// testing purposes only

	/**
	 * for testing purposes only. Do not use.
	 */
	public void create() throws IOException {
		folder = createTemporaryFolderIn(parentFolder);
	}

	/**
	 * Returns a new fresh file with the given name under the temporary folder.
	 */
	public Path newFile(String fileName) throws IOException {
		Path file = getRoot().resolve(fileName);
		Files.createFile(file);
		return file;
	}

	/**
	 * Returns a new fresh file with a random name under the temporary folder.
	 */
	public Path newFile() throws IOException {
		return Files.createTempFile(getRoot(), "junit", null);
	}

	/**
	 * Returns a new fresh folder with the given name under the temporary
	 * folder.
	 */
	public Path newFolder(String folder) throws IOException {
		return newFolder(new String[]{folder});
	}

	/**
	 * Returns a new fresh folder with the given name(s) under the temporary
	 * folder.
	 */
	public Path newFolder(String... folderNames) throws IOException {
		Path file = getRoot();
		for (int i = 0; i < folderNames.length; i++) {
			String folderName = folderNames[i];
			validateFolderName(folderName);
			file = file.resolve(folderName);
			if (!Files.exists(file)) {
				Files.createDirectory(file);
			}
		}
		return file;
	}

	/**
	 * Validates if multiple path components were used while creating a folder.
	 *
	 * @param folderName
	 *            Name of the folder being created
	 */
	private void validateFolderName(String folderName) throws IOException {
		Path tempFile = Paths.get(folderName);
		if (tempFile.getParent() != null) {
			String errorMsg = "Folder name cannot consist of multiple path components separated by a file separator."
					+ " Please use newFolder('MyParentFolder','MyFolder') to create hierarchies of folders";
			throw new IOException(errorMsg);
		}
	}

	private boolean isLastElementInArray(int index, String[] array) {
		return index == array.length - 1;
	}

	/**
	 * Returns a new fresh folder with a random name under the temporary folder.
	 */
	public Path newFolder() throws IOException {
		return createTemporaryFolderIn(getRoot());
	}

	private Path createTemporaryFolderIn(Path parentFolder) throws IOException {
		return Files.createTempDirectory(parentFolder, "junit");
	}

	/**
	 * @return the location of this temporary folder.
	 */
	public Path getRoot() {
		if (folder == null) {
			throw new IllegalStateException(
					"the temporary folder has not yet been created");
		}
		return folder;
	}

	/**
	 * Delete all files and folders under the temporary folder. Usually not
	 * called directly, since it is automatically applied by the {@link Rule}
	 */
	public void delete() {
		if (folder != null) {
			recursiveDelete(folder);
		}
	}

	private void recursiveDelete(Path file) {
		try {
			if (Files.isDirectory(file)) {
				Files.list(file).forEach(this::recursiveDelete);
			}
			Files.delete(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
