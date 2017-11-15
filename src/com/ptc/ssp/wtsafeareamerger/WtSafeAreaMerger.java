package com.ptc.ssp.wtsafeareamerger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

public class WtSafeAreaMerger
{
	private static Properties wtSafeAreaProperties = new Properties();
	//private static Properties productMappingProperties = new Properties();
	//private static String sourceVersion;
	private static String targetVersion;
	private static String safeAreaPath;
	private static String siteModName;
	private static String ptcMergeName;
	private static String opengrokURL;
	
	public static void main(String[] args)
	{
		String pathToSafeAreaProperties = args[0];
		try{
			wtSafeAreaProperties.load(new FileInputStream(new File(pathToSafeAreaProperties)));
			//productMappingProperties.load(new FileInputStream(new File("codebase/com/ptc/ssp/wtsafeareamerger/productmapping.properties")));
			//sourceVersion = wtSafeAreaProperties.getProperty("windchill.source.version");
			targetVersion = wtSafeAreaProperties.getProperty("windchill.target.version");
			opengrokURL = wtSafeAreaProperties.getProperty("opengrok.baseUrl");
//			boolean hasSource = Boolean.parseBoolean(wtSafeAreaProperties.getProperty("windchill.safearea.hasSource"));
//			if(!hasSource){
//				//Need to check class files & to download sources from Grok
//				//not supported yet
//				throw new UnsupportedOperationException("Not supported yet");
//			}
			safeAreaPath = wtSafeAreaProperties.getProperty("windchill.safearea.path");
			siteModName = wtSafeAreaProperties.getProperty("windchill.safearea.siteMod.name");
			ptcMergeName = wtSafeAreaProperties.getProperty("windchill.safearea.ptcMerge.name");
			File siteMod = new File(safeAreaPath + "/" + siteModName); 
			ArrayList<File> sourceFiles = new ArrayList<File>();
			ArrayList<String> allEntries = new ArrayList<>();
			recursiveBrowse(siteMod, sourceFiles, allEntries);
			for(File sourceFile:sourceFiles)
			{
				String sourcePath = sourceFile.getAbsolutePath();
				sourcePath = sourcePath.substring(sourcePath.indexOf(siteModName)+siteModName.length()+1);
				String normalizedSourcePath = sourcePath.replaceAll(Matcher.quoteReplacement(File.separator), "/");
				String downloadableFile = findTargetResource(normalizedSourcePath);
				if(downloadableFile != null){ 
					File file = new File(safeAreaPath + "/" + ptcMergeName + "/" + normalizedSourcePath);
					System.out.println("Downloading resource "+downloadableFile+" for merge");
					FileUtils.copyURLToFile(new URL(downloadableFile), file);
					
					ReportGenerator report = new ReportGenerator(sourceFile, file);
					String reportString = report.getCodeDiffReport();
					File reportFile = new File(safeAreaPath + "/" + ptcMergeName + "/report/" + normalizedSourcePath+"_report.html");
					FileUtils.writeStringToFile(reportFile, reportString);
				}
				else{
					System.err.println("Could not find resource "+normalizedSourcePath+" in project "+targetVersion);
				}
			}
			String reportString = ReportGenerator.getDiffTOC(allEntries);
			File reportFile = new File(safeAreaPath + "/" + ptcMergeName + "/report/ToC_report.html");
			FileUtils.writeStringToFile(reportFile, reportString);
			
		} 
		catch (FileNotFoundException e)	{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (IOException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void recursiveBrowse(File parent, ArrayList<File> files, ArrayList<String> allEntries)
	{
		File[] children = parent.listFiles();
		if(children != null && children.length>0)
		{
			for(File child:children)
			{
				allEntries.add(child.getAbsolutePath());
				if(child.isDirectory()){
					recursiveBrowse(child, files, allEntries);
				}
				else{
					files.add(child);
				}
			}
		}
	}
	
	
	
	public static String findTargetResource(String sourceFilePath) throws IOException
	{
		String downloadableFile = null;
		String resourceName = sourceFilePath.substring(sourceFilePath.lastIndexOf("/")+1);
		String comparableSourcePath;
		if(sourceFilePath.startsWith("src/")){
			comparableSourcePath=sourceFilePath.substring(3);
		}
		else if(sourceFilePath.startsWith("codebase/")){
			comparableSourcePath=sourceFilePath.substring(8);
		}
		else{
			comparableSourcePath="/"+sourceFilePath;
		}
		System.out.println("Looking for "+comparableSourcePath);
		String grokUrl = opengrokURL+"search?q=&project="+targetVersion+"&defs=&refs=&path="+resourceName+"&hist=";
		Document page = Jsoup.connect(grokUrl).timeout(0).get();
		Element results = page.select("div#results").first();
		Element table = results.select("table").first();
		if(table==null){
			return null;
		}
		Iterator<Element> resultRows = table.select("tr").iterator();
		while(resultRows.hasNext())
		{
			Element tr = resultRows.next();
			Element a = tr.select("a").first();
			if(a != null)
			{
				String href = a.attr("href");
				String targetResourceName = href.substring(href.indexOf("/src"));
				if (targetResourceName.startsWith("/src_web/"))
				{
					if (targetResourceName.substring(8).equals(comparableSourcePath)){
						downloadableFile = opengrokURL + "raw/" + href.substring(href.indexOf(targetVersion.replaceAll(" ", "%20")));
						return downloadableFile;
					}
				} 
				else if (targetResourceName.startsWith("/src/"))
				{
					if (targetResourceName.substring(4).equals(comparableSourcePath)){
						downloadableFile = opengrokURL + "raw/" + href.substring(href.indexOf(targetVersion.replaceAll(" ", "%20")));
						return downloadableFile;
					}
				}
			}
		}
		return downloadableFile;
		
	}
}
