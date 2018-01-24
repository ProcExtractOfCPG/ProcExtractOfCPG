import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.reflect.Array;
import java.util.HashMap;

public class POSTagger
{
	static final String StanfordParserPath = "/home/michl/Studienarbeit/stanford-parser-full-2017-06-09";
	static final String MetaMapPath = "/home/michl/Studienarbeit/MetaMap/public_mm";
	static final String LocalPath = "/home/michl/IdeaProjects/TestStanfordParser";

	private static Sentence[] Sentences;

	private static PrintStream allSemTypes;

	private static DictionaryLemmatizer lemmatizer;

	private static HashMap<String, String> VerbClasses;

	public static void initializeHMVerbClasses() {
		VerbClasses = new HashMap<String, String>();
		VerbClasses.put("start", "start");
		VerbClasses.put("initiate", "start");
		VerbClasses.put("perform", "perform");
		VerbClasses.put("conduct", "perform");
		VerbClasses.put("undertake", "perform");
		VerbClasses.put("escalate", "perform");
		VerbClasses.put("use", "use");
		VerbClasses.put("utilisize", "use");
		VerbClasses.put("have", "xxx");
		VerbClasses.put("show", "xxx");
	}

	public static void main(String[] args) throws Exception {

		File guideline = new File("guideline.txt");
		allSemTypes = new PrintStream(new FileOutputStream("AllSemTypes.txt"));

		String guidelineText = getGuidelineText(guideline);

		guidelineText = preProcessing(guidelineText);

		//guidelineText = cleanNonAsciiChararacters(guidelineText);

		OpenNLPTasks(guidelineText);

		for (int i = 0; i < Sentences.length; i++) {

			getPOSTags(i);
			getMetaMapAnnotations(i);
			parseMetaMapAnnotations(i);

		}

		System.setOut(new PrintStream(new FileOutputStream("AllSemTypes.txt")));
		initializeHMVerbClasses();
		for (int i = 0; i < Sentences.length; i++) {
			getVerbClass(i);
			printEnhancedParseTree(i);
		}

		return;
	}

	/**
	 * Read the text from the guideline file into a string
	 *
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static String getGuidelineText(File file) throws IOException {
		FileReader fr = new FileReader(file);
		final char[] temp = new char[(int) file.length()];
		fr.read(temp);
		fr.close();
		return new String(temp);
	}

	/**
	 * MetaMap only can handle with ascii characters. So all common non ascii characters will be transformed
	 * into proper ascii characters.
	 *
	 * @param guidelineText
	 */
	public static String cleanNonAsciiChararacters(String guidelineText) {
		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < guidelineText.length() ;i++){
			//if (String.valueOf(guidelineText.charAt(i)).matches("[^\\p{ASCII}]"))
			if (String.valueOf(guidelineText.charAt(i)).matches("[^\\x00-\\x7F]"))
			{
				int ch = (int)guidelineText.charAt(i);
				if (ch == 8805)
					sb.append(">=");
				else if (ch == 8804)
					sb.append("<=");
				else if (ch == 8222)
					sb.append("\"");
				else if (ch == 8221)
					sb.append("\"");
				else if (ch == 8220)
					sb.append("\"");
				else
					sb.append("[CODE#").append(ch).append("]");
			}else{
				sb.append(guidelineText.charAt(i));
			}
		}
		guidelineText = sb.toString();
		if (guidelineText.contains("[CODE#"))
			System.out.println("Warning! Input text contains non-ascii characters: " + guidelineText.substring(guidelineText.indexOf("[CODE#"), guidelineText.indexOf("[CODE#") + 11));
		return guidelineText;
	}

	/**
	 * MetaMap can't deal with newlines inside of sentences. Therefore this function
	 * removes double newlines with a period. So titles and subtitles won't be concatenated
	 * with the following sentences and no error will happen at MetaMap
	 *
	 * @param guidelineText
	 */
	public static String preProcessing(String guidelineText) {


		StringBuffer sb = new StringBuffer();
		for(int i = 0; i < guidelineText.length(); i++) {
			if ((int)guidelineText.charAt(i) == 10
					&& i + 1 < guidelineText.length()
					&& (int)guidelineText.charAt(i + 1) == 10
					&& guidelineText.charAt(i - 1) != 46
					&& guidelineText.charAt(i - 2) != 46)
			{
				// make points for sentences when you find titles with newlines and without periods
				sb.append(". ");
				i++;
			} else if ((int)guidelineText.charAt(i) == 47)
			{
				// from the token X/Y make two words X or Y
				// so the following syntax tree also handles them as two words
				sb.append(" or ");
			} else {
				sb.append(guidelineText.charAt(i));
			}
		}

		//
		guidelineText = cleanNonAsciiChararacters(sb.toString());

		// from the token X/Y make two words X or Y
		// so the following syntax tree also handles them as two words
		//guidelineText.replace("/", " or ");

		System.out.println(guidelineText);
		return guidelineText;
	}

	public static void OpenNLPTasks(String guidelineText){
		try(InputStream modelIn = new FileInputStream("models/en-sent.bin")){
			// load sentence model
			SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

			// split guideline text into sentences
			String sentences[] = sentenceDetector.sentDetect(guidelineText);
			Sentences = new Sentence[sentences.length];
			for (int i = 0; i < sentences.length; i++) {
				Sentences[i] = new Sentence(sentences[i]);
				getParseTree(String.join(" ", tokenizer(sentences[i], i)), i);     // TODO: auslagern in andere Funktion
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return;
	}

	public static String[] tokenizer(String sentence, int sentenceNr) throws FileNotFoundException {
		try (InputStream modelIn = new FileInputStream("models/en-token.bin")) {
			// load tokenizer model
			TokenizerModel model = new TokenizerModel(modelIn);
			Tokenizer tokenizer = new TokenizerME(model);

			// tokenize the sentence
			String[] tokens = tokenizer.tokenize(sentence);
			Span[] spans = tokenizer.tokenizePos(sentence);

			// testing and correcting error in brace splitting
			// if a word wouldn't splitted correctly from the braces, split them and expand the token array
			for (int i = 0; i < tokens.length; i++) {
				int tl = tokens.length;
				int x = tokens[i].length();
				if (x > 1 && (int)tokens[i].charAt(x-1) == 41)
				{
					String[] newTokens = new String[tl + 1];
					System.arraycopy(tokens, 0, newTokens, 0, i);
					System.arraycopy(tokens, i + 1, newTokens, i + 2, tl - i - 1);
					newTokens[i] = tokens[i].substring(0, tokens[i].length() - 1);
					newTokens[i + 1] = ")";
					tokens = newTokens;

					Span[] newSpans = new Span[tl + 1];
					System.arraycopy(spans, 0, newSpans, 0, i + 1);
					System.arraycopy(spans, i + 1, newSpans, i + 2, tl - i - 1);
					newSpans[i + 1] = spans[i];
					spans = newSpans;
					i++;
				}
				if (x > 1 && (int)tokens[i].charAt(0) == 40)
				{
					String[] newTokens = new String[tl + 1];
					System.arraycopy(tokens, 0, newTokens, 0, i);
					System.arraycopy(tokens, i + 1, newTokens, i + 2, tl - i - 1);
					newTokens[i] = "(";
					newTokens[i + 1] = tokens[i].substring(1, tokens[i].length());
					tokens = newTokens;

					Span[] newSpans = new Span[tl + 1];
					System.arraycopy(spans, 0, newSpans, 0, i + 1);
					System.arraycopy(spans, i + 1, newSpans, i + 2, tl - i - 1);
					newSpans[i + 1] = spans[i];
					spans = newSpans;
					i++;
				}

//				System.out.print(tokens.length + ":  ");
//				for (int j = 0; j < tokens.length; j++) {
//					System.out.print(tokens[j] + " # ");
//				}
//				System.out.println();
			}

			// put tokens into the sentence array
			Sentences[sentenceNr].setSentence(tokens, spans);
			return tokenizer.tokenize(sentence);        // TODO: evtl. auf void umstellen
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void getParseTree(String sentence, int sentenceNr) throws FileNotFoundException {
		InputStream modelIn = new FileInputStream("models/en-parser-chunking.bin");
		try {
			// load parse tree model
			ParserModel model = new ParserModel(modelIn);
			Parser parser = ParserFactory.create(model);

			Parse topParses[] = ParserTool.parseLine(sentence, parser, 1);
			System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
			System.out.println("Parsing of sentence " + sentenceNr + " of " + Sentences.length
					+ ": " + Sentences[sentenceNr].getText());
			Sentences[sentenceNr].setParseTree(topParses);
			System.out.println("###: ");
			topParses[0].show();

			System.setOut(new PrintStream(new FileOutputStream("ParseTree.txt")));
			topParses[0].show();
			BufferedReader brPT = new BufferedReader(new FileReader("ParseTree.txt"));
			Sentences[sentenceNr].setEnhancedParseTree(brPT.readLine());
			brPT.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if (modelIn != null) {
				try {
					modelIn.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	public static void getMetaMapAnnotations(int sentenceIndex) throws Exception {
		/*
		 * use MetaMap for medical annotations
		 */
		FileWriter fw = null;
		try {
			fw = new FileWriter("MetaMapInput.txt");
			fw.write(Sentences[sentenceIndex].getText());
			fw.append(System.getProperty("line.separator"));
		}
		catch (IOException e) {
			System.err.println("Konnte MetaMapInput-Datei nicht schreiben!");
		}
		finally {
			if (fw != null)
				try { fw.close(); } catch (IOException e) { e.printStackTrace(); }
		}
		System.setOut(new PrintStream(new FileOutputStream("PMElog.txt")));

		String metamapOptions = "";
		metamapOptions += "-y";					// Word Sense Disambiguation
		metamapOptions += "-a";					// for abbrevations
		metamapOptions += "--XMLn";				// Output as XML file
		//metamapOptions += "--UDA UDAfile";	// self defined abbrevations

		String ProjectFilePath = "/home/michl/IdeaProjects/ProcExtractOfCPG/";
		String MMInputFilePath = ProjectFilePath + "MetaMapInput.txt";
		String MMOutputFilePath = ProjectFilePath + "MetaMapOutput.xml";

		String command = MetaMapPath + "/bin/metamap -y -a --XMLn " + MMInputFilePath + " " + MMOutputFilePath;	// TODO: --XMLn implementieren
		System.out.println(command);
		try {
			Runtime rt = Runtime.getRuntime();
			//rt.exec(MetaMapPath + "/bin/skrmedpostctl start"); rt.exec(MetaMapPath + "/bin/wsdserverctl start");
			//rt.exec(MetaMapPath + "/bin/mmserver");
			Process process = rt.exec(command);

			BufferedReader stdInput = new BufferedReader(new
					InputStreamReader(process.getInputStream()));

			BufferedReader stdError = new BufferedReader(new
					InputStreamReader(process.getErrorStream()));
			// read the output from the command

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}

			File original = new File("MetaMapOutput.xml");
			File kopie = new File("MetaMapOutput2.xml");
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(original)));
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(kopie)));
			int counter = 0;
			String line;
			while((line = br.readLine()) != null){
				if(counter != 1){
					bw.write(line);
					bw.newLine();
				}
				counter++;
			}
			bw.close();
			br.close();

			original.delete();
			kopie.renameTo(new File("MetaMapOutput.xml"));

		}
		catch (Exception e) {
			System.err.println("Fehler: " + e);
		}
	}

	public static void parseMetaMapAnnotations(int sentenceIndex){
		try {
			String enhancedParseTree = Sentences[sentenceIndex].getEnhancedParseTree();

			// read MetaMap xml file
			File inputFile = new File("MetaMapOutput.xml");
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();
			NodeList nlPhrase = doc.getElementsByTagName("Phrase");



			Token[] sentenceToken = Sentences[sentenceIndex].getTokens();
//			System.out.println("Sentence length: " + sentenceToken.length);
//			for (int i = 0; i < sentenceToken.length; i++) {
//				System.out.print(sentenceToken[i].getWord() + " ");
//			}
//			System.out.println("~~~");
			int wordPointer = 0;
			String annoSemTypeID = "####";	// for case that annotation not found
			String annoSemGroupID = "####";	// for case that annotation not found

			// loop through all phrases in metamap file
			for (int i = 0; i < nlPhrase.getLength(); i++) {
				Node nPhrase = nlPhrase.item(i);
				Element ePhrase = (Element) nPhrase;
				//TODO: evtl. noch durch die Mappings loopen und besten Score auswählen
				NodeList nlMappingCandidates = ePhrase.getElementsByTagName("Candidate");

				// loop through all annotated words
				for (int j = 0; j < nlMappingCandidates.getLength(); j++) {
					//BufferedReader brPT = new BufferedReader(new FileReader("ParseTree.txt"));
					try {
						//String parseTree = brPT.readLine();
						Node nCandidate = nlMappingCandidates.item(j);
						Element eCandidate = (Element) nCandidate;
						int metamapTokenStartPos = Integer.parseInt(eCandidate
													.getElementsByTagName("StartPos")
													.item(0)
													.getTextContent());
						int metamapTokenLen = Integer.parseInt(eCandidate
													.getElementsByTagName("Length")
													.item(0)
													.getTextContent());
						int metamapMatchedWordsCount = Integer.parseInt(
													((Element)eCandidate
														.getElementsByTagName("MatchedWords")
														.item(0))
													.getAttribute("Count"));
						int metamapConceptPIsCount = Integer.parseInt(
													((Element)eCandidate
														.getElementsByTagName("ConceptPIs")
														.item(0))
													.getAttribute("Count"));
						String metamapToken = eCandidate
													.getElementsByTagName("MatchedWord")
													.item(0)
													.getTextContent();
						String annoSemType = eCandidate
													.getElementsByTagName("SemType")
													.item(0)
													.getTextContent();

						// look for annotated word in sentence to enhance the parse tree
						for (int k = wordPointer; k < sentenceToken.length; k++) {
							// founded metamap word matched perfectly
							if (sentenceToken[k].getWord().equals(metamapToken) && metamapMatchedWordsCount == 1)
							{
								sentenceToken[k].setMetaMapAnnotation(annoSemType);
								wordPointer = k++;
								break;
							}
							// founded metamap word is lemma or similar (abbrevation) and match
							else if (metamapTokenStartPos == sentenceToken[k].getPosition()
									&& metamapTokenLen == sentenceToken[k].getLength()
									&& metamapMatchedWordsCount == 1)
							{
								sentenceToken[k].setMetaMapAnnotation(annoSemType);
								wordPointer = k++;
								break;
							}
							// founded metamap word is a connected phrase
							else if (metamapConceptPIsCount == 1 && metamapMatchedWordsCount > 1)
							{
								int metamapMatchMapsCount = Integer.parseInt(
										((Element)eCandidate
												.getElementsByTagName("MatchMaps")
												.item(0))
												.getAttribute("Count"));


								int l = k;
								String aktToken = sentenceToken[l].getWord();
								int aktTokenLengths = sentenceToken[l].getLength();
								while (metamapTokenLen > aktTokenLengths && l < sentenceToken.length - 1)
								{
									l++;
									aktTokenLengths += sentenceToken[l].getLength() + 1;
								}
								if (sentenceToken[k].getPosition() == metamapTokenStartPos && metamapTokenLen == aktTokenLengths)
								{
									for (int m = k; m < l +1; m++) {
										wordPointer = m;
										sentenceToken[wordPointer].setMetaMapAnnotation(annoSemType);
									}
									break;
								}
							}
							// founded metamap word is a phrase with gap
							else if (metamapConceptPIsCount > 1)
							{
								int ks = k;
								for (int n = 0; n < metamapConceptPIsCount; n++) {
									metamapTokenStartPos = Integer.parseInt(eCandidate
											.getElementsByTagName("StartPos")
											.item(n)
											.getTextContent());
									metamapTokenLen = Integer.parseInt(eCandidate
											.getElementsByTagName("Length")
											.item(n)
											.getTextContent());
									metamapToken = eCandidate
											.getElementsByTagName("MatchedWord")
											.item(n)
											.getTextContent();

									int l = ks;
									String aktToken = sentenceToken[l].getWord();
									int aktTokenLengths = sentenceToken[l].getLength();
									while (metamapTokenLen > aktTokenLengths && l + 1 < sentenceToken.length)
									{
										l++;
										aktTokenLengths += sentenceToken[l].getLength() + 1;
									}
									if (sentenceToken[ks].getPosition() == metamapTokenStartPos && metamapTokenLen == aktTokenLengths)
									{
										for (int m = ks; m < l + 1; m++)
											sentenceToken[m].setMetaMapAnnotation(annoSemType);
									}
								}

							}
						}

						// add MetaMapAnnotation to token

						// get ID for semantic type
						BufferedReader brSTaG = new BufferedReader(new FileReader("SemanticTypes.txt"));
						while ((annoSemTypeID = brSTaG.readLine()) != null){
							if (annoSemTypeID.substring(0, annoSemTypeID.indexOf("|")).equals(annoSemType)) {
								annoSemTypeID = annoSemTypeID.substring(annoSemTypeID.indexOf("|") + 1,
										annoSemTypeID.indexOf("|", annoSemTypeID.indexOf("|") + 1));
								brSTaG.close();
								break; }
						}
						brSTaG.close();

						// get semantic group by the ID
						brSTaG = new BufferedReader(new FileReader("SemanticGroups.txt"));
						while ((annoSemGroupID = brSTaG.readLine()) != null){
							int pipeStart = annoSemGroupID.indexOf("|", 5);
							String loopSemGroupID = annoSemGroupID.substring(pipeStart + 1, pipeStart + 5);
							if (loopSemGroupID.equals(annoSemTypeID)) {
								annoSemGroupID = annoSemGroupID.substring(5, annoSemGroupID.indexOf("|", 5));
								brSTaG.close();
								break;
							}
						}
						brSTaG.close();

//						System.out.println(sentenceToken[wordPointer].getPOSTag());
						if (!sentenceToken[wordPointer].getPOSTag().startsWith("VB"))
						{
							// enhance parse tree with metamap annotations
							// TODO: insert inner nodes when annotation consists of more words for tregex
//							int ptCharPointer = 0;
//							int ptWordPointer = 0;
//							char firstChar = 0x00;
//							char secondChar = 0x00;
//
//							while (ptWordPointer - 1 < wordPointer
//									&& ptCharPointer < enhancedParseTree.length())
//							{
////							String debugC = Character.toString(enhancedParseTree.charAt(ptCharPointer));
//								// found the start of a token or new opening brace
//								if (firstChar == 0x28 && secondChar == 0x20)
//								{
//									// found the start of a token
//									if (enhancedParseTree.charAt(ptCharPointer) != 0x28)	// no brace
//									{
//										Sentences[sentenceIndex].getTokens()[ptWordPointer].setPosition(ptCharPointer);
//										ptWordPointer++;
//										firstChar = 0x00;
//										secondChar = 0x00;
//									}
//									else
//										secondChar = 0x00;
//								}
//								// found space after an opening brace
//								else if (firstChar == 0x28 && enhancedParseTree.charAt(ptCharPointer) == 0x20)
//									secondChar = 0x20;
//
//									// found opening brace
//								else if (enhancedParseTree.charAt(ptCharPointer) == 0x28)
//									firstChar = 0x28;
//								ptCharPointer++;
//							}
//
//							// replace token with annotation in parse tree
//							int x = sentenceToken[wordPointer].getLength();
//							int y = ptCharPointer - 1 + sentenceToken[wordPointer].getLength();
//							int z = enhancedParseTree.length();

							int wordPosInParseTree = Sentences[sentenceIndex].getTokens()[wordPointer].getPositionInParseTree();
							enhancedParseTree = enhancedParseTree.substring(0, wordPosInParseTree - 1) + "[" + annoSemType + "]"
									+ enhancedParseTree.substring(
											wordPosInParseTree - 1 + Sentences[sentenceIndex].getTokens()[wordPointer].getLengthInParseTree());

							System.setOut(allSemTypes);
							System.out.println(enhancedParseTree);
							for (int k = wordPointer + 1; k < Sentences[sentenceIndex].getSentenceLength(); k++) {
								int newPos = annoSemType.length() + 2 - Sentences[sentenceIndex].getTokens()[wordPointer].getLengthInParseTree()
										+ Sentences[sentenceIndex].getTokens()[k].getPositionInParseTree();
								Sentences[sentenceIndex].getTokens()[k].setPositionInParseTree(newPos);
							}
							Sentences[sentenceIndex].getTokens()[wordPointer].setLengthInParseTree(annoSemType.length() + 2);

							for (int k = 0; k < Sentences[sentenceIndex].getSentenceLength(); k++) {
								System.out.println(Sentences[sentenceIndex].getTokens()[k].getWord() + ": "
										+ Sentences[sentenceIndex].getTokens()[k].getPositionInParseTree());
							}
//							System.setOut(allSemTypes);
//							System.out.println();
//							System.out.println(sentenceToken[wordPointer].getWord() +";" + annoSemType + ";" + annoSemTypeID + ";" + annoSemGroupID);

						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
						System.out.println("Fehler bei Wort : " + annoSemTypeID + ";" + annoSemGroupID);
					}
				}
			}

			System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
			System.out.println();
			Token[] sentence = Sentences[sentenceIndex].getTokens();
			for (int i = 0; i < sentence.length; i++) {
				System.out.print(sentence[i].getWord() + " ");
			}
			System.out.println();
			System.out.println();
			for (int i = 0; i < sentence.length; i++) {
				if (sentence[i].getMetaMapAnnotation() == null)
					System.out.print(sentence[i].getWord() + " ");
				else
					System.out.print("[" + sentence[i].getMetaMapAnnotation() + "] ");
			}
			System.out.println();
			System.out.println();
			System.out.println(enhancedParseTree);

			Sentences[sentenceIndex].setEnhancedParseTree(enhancedParseTree);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	//Given  local epidemiology, empiric coverage for Gramnegative organisms should also be added pending culture results if the patient shows evidence of septic shock, has a hemodialysis catheter, is neutropenic, has a femoral CVC, has a history of short gut syndrome, or history of a solid organ transplant (Figure 2, Box 3).

	public static void getPOSTags(int sentenceIndex){
		int wordPointer = 0;
		int ptCharPointer = 0;
		String parseTree = Sentences[sentenceIndex].getEnhancedParseTree();
		////System.out.println("xxxxx: "+parseTree);
		String POSTag = "";
		Character firstChar = 0x00;
		Character secondChar = 0x00;

		while (ptCharPointer < parseTree.length() && wordPointer < Sentences[sentenceIndex].getSentenceLength())
		{
			Character ch = parseTree.charAt(ptCharPointer);
			// found opening brace
			if (firstChar == 0x28 && secondChar == 0x20 && ch != 0x20 && ch != 0x28)
			{
				Sentences[sentenceIndex].getTokens()[wordPointer].setPOSTag(POSTag);
				Sentences[sentenceIndex].getTokens()[wordPointer].setPositionInParseTree(ptCharPointer + 1);
				POSTag = "";
				wordPointer++;
				firstChar = 0x00;
				secondChar = 0x00;
			}
			// found character after an opening brace
			else if (firstChar == 0x28 && ch != 0x20 && ch != 0x28){
				POSTag += Character.toString(parseTree.charAt(ptCharPointer));
			}
			else if (firstChar == 0x28 && ch == 0x20)
			{
				secondChar = 0x20;
			}
			else if (ch == 0x28) {
				firstChar = 0x28;
				secondChar = 0x00;
				POSTag = "";
			}
			ptCharPointer++;
		}

//		for (int i = 0; i < Sentences[sentenceIndex].getTokens().length; i++) {
//			System.out.println(Sentences[sentenceIndex].getTokens()[i].getPOSTag() + " --- " +
//					Sentences[sentenceIndex].getTokens()[i].getWord());
//		}
	}

	public static String[] getLemmas(int sentenceIndex) {
		Token[] token = Sentences[sentenceIndex].getTokens();

		if (lemmatizer == null) {
			InputStream is = null;
			try {
				is = new FileInputStream("models/en-lemmatizer.dict");
				lemmatizer = new DictionaryLemmatizer(is);
				is.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		String[] tokens = Sentences[sentenceIndex].getAllWords();
		String[] postags = Sentences[sentenceIndex].getAllPOSTags();

		String[] lemmas = lemmatizer.lemmatize(tokens, postags);

		for (int i = 0; i < lemmas.length; i++) {
			if (postags[i].startsWith("VB"))
				System.out.println(token[i].getPOSTag() + " *** " + lemmas[i] + " - ");// + token[i].getWord());
				//TODO: ersetzen durch zugehörige Verbklasse
		}
		return lemmas;
	}

	public static String matchVerbClass(String verb) {

		return VerbClasses.get(verb);
	}

	public static void getVerbClass(int sentenceIndex){

		if (lemmatizer == null) {
			InputStream is = null;
			try {
				is = new FileInputStream("en-lemmatizer.bin");
				lemmatizer = new DictionaryLemmatizer(is);
				is.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		String[] tokens = Sentences[sentenceIndex].getAllWords();
		String[] postags = Sentences[sentenceIndex].getAllPOSTags();

		String[] lemmas = lemmatizer.lemmatize(tokens, postags);

		String enhancedParseTree = Sentences[sentenceIndex].getEnhancedParseTree();

		for (int i = 0; i < lemmas.length; i++) {
			if (postags[i].startsWith("VB"))
			{
				//TODO: ersetzen durch zugehörige Verbklasse
				if (VerbClasses.get(lemmas[i]) != null)
				{
					////System.out.println(lemmas[i] + " -- " + Sentences[sentenceIndex].getTokens()[i].getPositionInParseTree());

					enhancedParseTree = enhancedParseTree.substring(0, Sentences[sentenceIndex].getTokens()[i].getPositionInParseTree() - 1
							) + "[" + VerbClasses.get(lemmas[i]) + "]"
							+ enhancedParseTree.substring(Sentences[sentenceIndex].getTokens()[i].getPositionInParseTree() - 1
							+ tokens[i].length());

					for (int j = i + 1; j < Sentences[sentenceIndex].getSentenceLength(); j++) {
						int newPos = 1 + lemmas[i].length() - tokens[i].length() + Sentences[sentenceIndex].getTokens()[j].getPositionInParseTree();
						Sentences[sentenceIndex].getTokens()[j].setPositionInParseTree(newPos);
					}

//					System.out.println(enhancedParseTree);

				}
			}

		}
	}

	public static void printEnhancedParseTree(int sentenceIndex) {
		System.out.println(Sentences[sentenceIndex].getEnhancedParseTree());
		System.out.println();
	}
}
