/*
*  Lexicon.java
*
*  Copyright (c) 2001-2005, The University of Sheffield.
*
*  This file is part of GATE (see http://gate.ac.uk/), and is free
*  software, licenced under the GNU Library General Public License,
*  Version 2, June 1991 (in the distribution as file licence.html,
*  and also available at http://gate.ac.uk/gate/licence.html).
*
*  HepTag was originally written by Mark Hepple, this version contains
*  modifications by Valentin Tablan and Niraj Aswani.
*
*  $Id: Lexicon.java,v 1.1 2005/09/30 14:48:12 ian_roberts Exp $
*/
package hepple.postag ;

/**
* Title:        HepTag
* Description:  Mark Hepple's POS tagger
* Copyright:    Copyright (c) 2001
* Company:      University of Sheffield
* @author Mark Hepple
* @version 1.0
*/

import java.util.*;
import java.io.*;
import java.net.URL;

/**
* A {@link java.util.HashMap} that maps from lexical entry
* ({@link java.lang.String}) to possible POS categories
* ({@link java.util.List}
*/
public class Lexicon extends HashMap {

	private String encoding;


	/**
	* @deprecated The lexicon file is read at construction time, so setting the
	* encoding later will have no effect.  Use the two argument constructor to
	* set the encoding.
	*/
	public void setEncoding(String encoding) {
		throw new IllegalStateException("Cannot change encoding once POS tagger "
			+ "has been constructed.  Use the three "
			+ "argument constructor to specify "
			+ "encoding.");
	}

	/**
	* Constructor.
	* @param lexiconURL an URL for the file contianing the lexicon.
	*/
	public Lexicon(URL lexiconURL) throws IOException{
		this(lexiconURL, null);
	}

	/**
	* Constructor.
	* @param lexiconURL an URL for the file contianing the lexicon.
	* @param encoding the character encoding to use for reading the lexicon.
	*/
	public Lexicon(URL lexiconURL, String encoding) throws IOException{
		this.encoding = encoding;
		String line;
		BufferedReader lexiconReader;
		if(encoding == null) {
			lexiconReader = new BufferedReader(new InputStreamReader(lexiconURL.openStream()));
		} else {
			lexiconReader = new BufferedReader(new InputStreamReader(lexiconURL.openStream(),encoding));
		}

		line = lexiconReader.readLine();
		String entry;
		List categories;
		while(line != null){
			StringTokenizer tokens = new StringTokenizer(line);
			entry = tokens.nextToken();
			categories = new ArrayList();
			while(tokens.hasMoreTokens()) categories.add(tokens.nextToken());
			put(entry, categories);

			line = lexiconReader.readLine();
		}//while(line != null)
	}//public Lexicon(URL lexiconURL) throws IOException

}//class Lexicon