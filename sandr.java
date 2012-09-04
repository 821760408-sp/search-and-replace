/*
Usage: java sandr [options]

	options:
		
		-rk : indicating key is regex rather than literal text
		-rs : indicating substitute is regex rather than literal text
		-c  : indicating key is case sensitive
		-m  : indicating match key on word boundary rathen than in any substring
		-o  : indicating write output log file
	
	when prompted to enter:
		
		input path: source path containing the files to be processed, absolute or relative
		
		output path: path to the file for outputting a list of which files are modified
		
		key: text or pattern to be replaced if found in files
			e.g. "John Cage" (literal, defalut)
				"\s+(John|Nicholas)\s+Cage" (regex, turn flag "-rk" on)
		
		substitutition: text or pattern replacing the key
			same as key--literal is default, regex when turn on flag "-rs"
 */
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class sandr
{
	/** input directory */
	private File in;

	/** input files */
	private File[] inFiles;

	/** output log file */
	private File out;

	/** the key to search in the input files */
	private String key;

	/** the substitution replacing key, if key is found */
	private String sub;

	/** flag for whether to write output log file */
	private boolean toWrite;

	public static final int LITERAL_KEY = 0;		// key is literal
	public static final int REGEX_KEY = 1;			// key is regex
	public static final int LITERAL_SUB = 0;		// substitution is literal
	public static final int REGEX_SUB = 1;			// substitution is regex
	public static final int CASE_INSENSITIVE = 0;	// find key ignoring case
	public static final int CASE_SENSITIVE = 1;
	public static final int MATCH_ANY = 0;			// match any substring
	public static final int MATCH_WHOLE_WORD = 1;	// match on word boundary

	/**
	 *	Constructor, writing output.
	 *
	 *	@param in
	 *		input directory denoted by a File object
	 *
	 *	@param out
	 *		output log file denoted by a File object
	 *
	 *	@param key
	 *		a key to find in the input files
	 *
	 *	@param sub
	 *		a substitution to replace key, if key found
	 *
	 *	@throws IllegalArgumentException
	 *		indicating invalid parameters
	 */
	public sandr(File in, File out, String key, String sub)
	{
		this(in, key, sub);

		// check File out
		if (out == null)
			throw new IllegalArgumentException("Output path not exists.\n");
		this.out = out;

		toWrite = true;
	}

	public sandr(File in, String key, String sub)
	{
		// check File in
		if (!in.isDirectory())
			throw new IllegalArgumentException("Source path not exists or not directory.\n");
		this.in = in;

		inFiles = in.listFiles();

		// check String key
		if (key == null)
			throw new IllegalArgumentException("Key pattern not specified.\n");
		this.key = key;

		// check String sub
		if (sub == null)
			throw new IllegalArgumentException("Substitution pattern not specified.\n");
		this.sub = sub;
	}

	/** 
	 *	Process all target files in the specified input directory.
	 *
	 *	@param keyMode
	 *		sandr.LITERAL_KEY or sandr.REGEX_KEY
	 *
	 *	@param subMode
	 *		sandr.LITREAL_SUB or sandr.REGEX_SUB
	 *
	 *	@param caseMode
	 *		sandr.CASE_INSENSITIVE or sandr.CASE_SENSITIVE
	 *
	 *	@param matchMode
	 *		sandr.MATCH_ANY or sandr.MATCH_WHOLE_WORD
	 *	
	 *	@throws IOException
	 *		indicating errors occurs when writing log file
	 *
	 *	@throws IllegalArgumentException
	 *		if any invalid parameters
	 */
	public void sequentiallyProcessFiles(int keyMode, int subMode, int caseMode, int matchMode)
		throws IOException
	{
		// if directory is empty, no files to be processed, quit
		if (inFiles.length == 0) return;

		// check parameters
		if (keyMode != LITERAL_KEY && keyMode != REGEX_KEY)
			throw new IllegalArgumentException("Invalid arguments.");
		if (subMode != LITERAL_SUB && subMode != REGEX_SUB)
			throw new IllegalArgumentException("Invalid arguments.");
		if (caseMode != CASE_INSENSITIVE && caseMode != CASE_SENSITIVE)
			throw new IllegalArgumentException("Invalid arguments.");
		if (matchMode != MATCH_ANY && matchMode != MATCH_WHOLE_WORD)
			throw new IllegalArgumentException("Invalid arguments.");

		// create log file if specified
		PrintWriter outWriter = null; // write output
		if (toWrite) {
			outWriter = new PrintWriter(out);
			outWriter.printf("%-50s%s\n%-50s%s\n%-50s%s\n%-50s%s\n%-50s%s\n%-50s%s\n%-50s%s\n\n\n%s\n",
				"Input directory:", in.getCanonicalPath(),
				"Key pattern:", key,
				"Key pattern is regex or literal:", keyMode == LITERAL_KEY ? "literal" : "regex",
				"Substitution pattern:", sub,
				"Substitution pattern is regex or literal:", subMode == LITERAL_SUB ? "literal" : "regex",
				"Case sensitive:", caseMode == CASE_INSENSITIVE ? "no" : "yes",
				"Match any or match whole word:", matchMode == MATCH_ANY ? "any" : "whole word",
				"File modified:");
		}

		// Sequentially process all Files in the directory
		for (File inFile : inFiles) {	
			// only process file when not a sub-directory or restricted file
			// or the log file which happens to be created under the same directory
			if (isValid(inFile)) {	
				String fileContents = null;
				// first, backup file
				try	{ // if fails, skip this file and proceed
					fileContents = readAndBackup(inFile);
				} catch (IOException e) {
					if (outWriter != null)
						outWriter.println(inFile.getName() + "\t\tbackup failed; skipped");
					continue;
				}
				// next, process file and write log if specified
				try {
					processFile(inFile, fileContents, keyMode, subMode, caseMode, matchMode, outWriter);
				} catch (IOException e) {
					if (toWrite)
						outWriter.println(inFile.getName() + "\t\tcannot be processed; skipped");
					continue;
				}
			}
		}
		
		if (outWriter != null)
			outWriter.close();
	}

	/**
	 *	Method for processing a single file.
	 *	1. creates a new temp file,
	 *	2. match and replace original file contents,
	 *	3. write temp file; delete original file; rename temp file.
	 *	inFile is a single file from the specified directory.
	 *  fileContents is a String containing the whole contents of the original file.
	 *  outWriter is a PrintWriter to write log file.
	 */
	private void processFile(File inFile, String fileContents, 
							int keyMode, int subMode, 
							int caseMode, int matchMode, 
							PrintWriter outWriter) throws IOException
	{
		// create temp file to write a replacing file
		String inFileName = inFile.getName();
		String inFileParent = inFile.getParent();
		String tempFilePath = inFileParent + File.separator + "temp_" + inFileName;
		File tempFile = new File(tempFilePath);
		
		// build key Pattern for matching, using method buildPattern()
		Pattern keyPattern = buildPattern(key, keyMode, caseMode, matchMode);
	
		// prepare writing the replacing file
		FileWriter tempWriter = new FileWriter(tempFile);
		Matcher matcher = keyPattern.matcher(fileContents);	// create Matcher with original file contents
		String newFileContents = null;
		
		// match and replacing original file contents
		if (subMode == LITERAL_SUB)
			newFileContents = matcher.replaceAll(Matcher.quoteReplacement(sub));
		else
			newFileContents = matcher.replaceAll(sub);
		
		// write the replacing file
		try {
			tempWriter.write(newFileContents, 0, newFileContents.length());
			if (!newFileContents.equals(fileContents))	// if replaced
				outWriter.println(inFileName);	// write log file
		} finally {
			tempWriter.close();
		}

		// replace the original file with the temp file
		inFile.delete();
		tempFile.renameTo(new File(inFileParent + File.separator + inFileName));
	}

	private boolean isValid(File file)
	{
		// if File is a sub-directory, skip it
		try {
			if (!file.isFile()) return false;
		} catch (SecurityException e) {
			return false;
		}
		
		// if File is non-readable or non-writable
		try {
			if (!(file.canRead() && file.canWrite())) return false;
		} catch (SecurityException e) {
			return false;
		}

		// if File happens to be the log file
		if (out != null)
			if (file.compareTo(out) == 0)
				return false;

		return true;
	}

	/**
	 *	Method for reading and backing up file,
	 *  returns a String of the whole contents of the file.
	 */
	private String readAndBackup(File file) throws IOException
	{
		FileReader input = new FileReader(file); // prepare to read input file

		StringBuilder stringBuilder = new StringBuilder(512); // for storing contents
		
		// create copy path
		String inputParent = file.getParent();
		if (inputParent == null) inputParent = ".";
		String outputPath = inputParent + File.separator + "backup_" + file.getName();
		
		try	{ // if writing copy fails, close original file
			FileWriter output = new FileWriter(new File(outputPath)); // prepare to write copy
			int c = 0;
			try {
				while ((c = input.read()) != -1)
					stringBuilder.append((char) c);
				output.write(stringBuilder.toString(), 0, stringBuilder.toString().length());
			} finally {
				output.close();
			}
		} finally {
			input.close();
		}

		return stringBuilder.toString();
	}

	private Pattern buildPattern(String key, int keyMode, int caseMode, int matchMode)
	{
		if (keyMode == REGEX_KEY)
			key = (matchMode == MATCH_ANY) ? key : ("\\b" + key + "\\b");
		if (keyMode == LITERAL_KEY)
			key = (matchMode == MATCH_ANY) ? Pattern.quote(key) : ("\\b" + Pattern.quote(key) + "\\b");
		if (caseMode == CASE_SENSITIVE)
			return Pattern.compile(key);
		return Pattern.compile(key, Pattern.CASE_INSENSITIVE);
	}

	/**
	 *	Method to safely exit when error occurs, displays usage.
	 */
	private static void exitWithHelp()
	{
		System.out.printf("\n%s\n\n%s\n%s\n%s\n%s\n%s\n%s\n\n%s\n%s\n\n%s\n\n%s\n%s\n%s\n\n%s\n%s\n\n",
			"Usage: java sandr [options]",
			"options:",
			"  -rk : indicating key is regex rather than literal text",
			"  -rs : indicating substitute is regex rather than literal text",
			"  -c  : indicating key is case sensitive",
			"  -m  : indicating match key on word boundary rathen than in any substring",
			"  -o  : indicating write output log file",
			"when prompted to enter:",
			" input path: source path containing the files to be processed, absolute or relative",
			" output path: path to the file for outputting a list of which files are modified",
			" key: text or pattern to be replaced if found in files",
			" 	e.g. \"John Cage\" (literal, defalut)",
			" 		\"\\s+(John|Nicholas)\\s+Cage\" (regex, turn flag \"-rk\" on)",
			" substitutition: text or pattern replacing the key",
			" 	same as key--literal is default, regex when turn on flag \"-rs\"");
		System.exit(1);
	}

	/**
	 *	Main method.
	 *
	 *	prompt user to enter:
	 *	input directory: an absolute path or a relative path like "./xxx" for input
	 *	output path: (if flag "-o" is on) absolute or relative path for log file
	 *	key: literal e.g. "John Cage" or regex e.g "\s+(John|Nicholas)\s+Cage"
	 *	substitution: literal or regex, same as key
	 *	
	 *	use various flags to set:
	 *	key mode (default: literal/ "-rk": regex)
	 *	substitution mode (default: literal/ "-rs": regex)
	 *	case mode (default: case insensitive/ "-c": sensitive)
	 *	match mode (default: match any substring/ "-m": on word boundary)
	 *
	 *	intialize a sandr and process the target files
	 */
	public static void main(String[] args)
	{
		File in = null;
		File out = null;
		String inputPath = null;
		String outputPath = null;
		String key = null;
		String sub = null;
		int keyMode = LITERAL_KEY;
		int subMode = LITERAL_KEY;
		int caseMode = CASE_INSENSITIVE;
		int matchMode = MATCH_ANY;
		boolean hasOutput = false;
		sandr sar = null;

		Scanner scanner = new Scanner(System.in);

		// parse command-line arguments
		for (String arg : args) {
			if (arg.startsWith("-")) {
				if (arg.equals("-rk"))
					keyMode = REGEX_KEY;
				else if (arg.equals("-rs"))
					subMode = REGEX_SUB;
				else if (arg.equals("-c"))
					caseMode = CASE_SENSITIVE;
				else if (arg.equals("-m"))
					matchMode = MATCH_WHOLE_WORD;
				else if (arg.equals("-o"))
					hasOutput = true;
				else {
					System.out.printf("Invalid flag %s\n", arg);
					exitWithHelp();
				}
			} else {
				System.out.printf("Invalid argument %s\n", arg);
				exitWithHelp();
			}
		}

		// prompt user for input directory
		System.out.println("Please enter the path to the directory of files:");
		inputPath = scanner.nextLine();
		in = new File(inputPath);
		if (!in.isDirectory()) {
			System.out.println("Source path not exists or not directory.\n");
			exitWithHelp();
		}

		// prompt user for output path
		if (hasOutput) {
			System.out.println("Please enter the path for outputting log file:");
			outputPath = scanner.nextLine();
			out = new File(outputPath);

			String outputPathParent = out.getParent();
			if (outputPathParent == null)
				outputPathParent = ".";
			File checkPath = new File(out.getParent());
			if (!checkPath.isDirectory()) {
				System.out.println("Output path not exists.");
				exitWithHelp();
			}
		}

		// prompt user for key and substitution
		System.out.println("Please enter the text or pattern to be matched (as String):");
		key = scanner.nextLine();
		System.out.println("Please enter the text or pattern to replace with (as String):");
		sub = scanner.nextLine();

		// instantiate sandr object
		try {
			if (hasOutput)
				sar = new sandr(in, out, key, sub);
			else
				sar = new sandr(in, key, sub);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			exitWithHelp();
		} catch (SecurityException e) {
			e.printStackTrace();
			System.err.println("Access denied.");
			System.exit(1);
		}

		// process files
		try {
			System.out.println("processing...");

			sar.sequentiallyProcessFiles(keyMode, subMode, caseMode, matchMode);
			
			System.out.println("done");
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Error writing log file; terminated");
			System.exit(1);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}