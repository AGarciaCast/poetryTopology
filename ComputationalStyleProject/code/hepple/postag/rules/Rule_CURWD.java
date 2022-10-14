/*
*  Copyright (c) 2001-2005, The University of Sheffield.
*
*  This file is part of GATE (see http://gate.ac.uk/), and
*  software, licenced under the GNU Library General Public
*  Version 2, June 1991 (in the distribution as file licen
*  and also available at http://gate.ac.uk/gate/licence.ht
*
*  HepTag was originally written by Mark Hepple, this vers
*  modifications by Valentin Tablan and Niraj Aswani.
*
*  $Id: Rule_CURWD.java,v 1.1 2005/09/30 14:48:12 ian_robe
*/

package hepple.postag.rules;

import hepple.postag.*;


/**
* Title:        HepTag
* Description:  Mark Hepple's POS tagger
* Copyright:    Copyright (c) 2001
* Company:      University of Sheffield
* @author Mark Hepple
* @version 1.0
*/

public class Rule_CURWD extends Rule {

	public Rule_CURWD() {
	}
	public boolean checkContext(POSTagger tagger) {
		return (tagger.wordBuff[3].equals(context[0]));
	}
}