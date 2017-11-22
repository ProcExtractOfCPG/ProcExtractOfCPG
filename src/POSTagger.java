import opennlp.tools.cmdline.parser.ParserTool;
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

public class POSTagger
{
	static final String StanfordParserPath = "/home/michl/Studienarbeit/stanford-parser-full-2017-06-09";
	static final String MetaMapPath = "/home/michl/Studienarbeit/MetaMap/public_mm";
	static final String LocalPath = "/home/michl/IdeaProjects/TestStanfordParser";

	private static Sentence[] Sentences;

	private static PrintStream allSemTypes;

	public static void main(String[] args) throws Exception {

		File guideline = new File("guideline.txt");
		allSemTypes = new PrintStream(new FileOutputStream("AllSemTypes.txt"));

//		getAllMetaMapAnnotationsForTesting();



		sentenceSplitter(guideline);

		for (int i = 0; i < Sentences.length; i++) {

			getMetaMapAnnotations(i);
			parseMetaMapAnnotations(i);
		}

		return;
	}


	public static void parseMetaMapAnnotations(int sentenceIndex){
		try {
			System.setOut(new PrintStream(new FileOutputStream("ParseTree.txt")));
			Sentences[sentenceIndex].getParseTree()[0].show();
			BufferedReader brPT = new BufferedReader(new FileReader("ParseTree.txt"));
			String enhancedParseTree = brPT.readLine();
			String enhancedParseTree2 = enhancedParseTree;
			brPT.close();

			// read MetaMap xml file
			File inputFile = new File("MetaMapOutput.xml");
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();
			NodeList nlPhrase = doc.getElementsByTagName("Phrase");



			Token sentenceToken[] = Sentences[sentenceIndex].getTokens();
			int wordPointer = 0;
			String annoSemTypeID = "####";	// for case that annotation not found
			String annoSemGroupID = "####";	// for case that annotation not found

			// loop through all phrases in metamap file
			for (int i = 0; i < nlPhrase.getLength(); i++) {
				Node nPhrase = nlPhrase.item(i);
				Element ePhrase = (Element) nPhrase;
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
								while (metamapTokenLen > aktTokenLengths)
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
						BufferedReader brSTaG = new BufferedReader(new FileReader("SemanticTypes.txt"));

						while ((annoSemTypeID = brSTaG.readLine()) != null){
							if (annoSemTypeID.substring(0, annoSemTypeID.indexOf("|")).equals(annoSemType)) {
								annoSemTypeID = annoSemTypeID.substring(annoSemTypeID.indexOf("|") + 1,
										annoSemTypeID.indexOf("|", annoSemTypeID.indexOf("|") + 1));
								brSTaG.close();
								break;
							}
						}
						brSTaG.close();

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

						// enhance parse tree with metamap annotations
						// TODO: insert inner nodes when annotation consists of more words for tregex
						int ptCharPointer = 0;
						int ptWordPointer = 0;
						char firstChar = 0x00;
						char secondChar = 0x00;

						while (ptWordPointer - 1 < wordPointer)
						{
//							String debugC = Character.toString(enhancedParseTree.charAt(ptCharPointer));
							if (firstChar == 0x28 && secondChar == 0x20)
							{
								if (enhancedParseTree.charAt(ptCharPointer) != 0x28)	// no brace
								{
									ptWordPointer++;
									firstChar = 0x00;
									secondChar = 0x00;
								}
								else
									secondChar = 0x00;
							}
							// found space after an opening brace
							else if (firstChar == 0x28 && enhancedParseTree.charAt(ptCharPointer) == 0x20)
								secondChar = 0x20;

							// found opening brace
							else if (enhancedParseTree.charAt(ptCharPointer) == 0x28)
								firstChar = 0x28;
							ptCharPointer++;
						}

						// replace token with annotation in parse tree
						enhancedParseTree = enhancedParseTree.substring(0, ptCharPointer -1) + "[" + annoSemType + "]"
								+ enhancedParseTree.substring(ptCharPointer -1 + sentenceToken[wordPointer].getLength());
						System.setOut(allSemTypes);
						System.out.println(sentenceToken[wordPointer].getWord() +";" + annoSemType + ";" + annoSemTypeID + ";" + annoSemGroupID);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}

			//
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
			System.out.println(enhancedParseTree2);
			System.out.println();
			System.out.println();
			System.out.println(enhancedParseTree);
			Sentences[sentenceIndex].setEnhancedParseTree(enhancedParseTree);
		}
		catch (Exception e) {
			e.printStackTrace();
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
		String command = MetaMapPath + "/bin/metamap -y -a --XMLn MetaMapInput.txt MetaMapOutput.xml";	// TODO: --XMLn implementieren

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

	public static void sentenceSplitter(File file){
		try(InputStream modelIn = new FileInputStream("en-sent.bin")){
            // load sentence model
            SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

			// read guideline file
			BufferedReader br = new BufferedReader(new FileReader(file));
			String guidelineText = "";
			String zeile;
			while ((zeile = br.readLine()) != null) { guidelineText += zeile; }
			br.close();

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
			System.out.println(e);
		}

		return;
	}

	public static String[] tokenizer(String sentence, int sentenceNr) throws FileNotFoundException {
		try (InputStream modelIn = new FileInputStream("en-token.bin")) {
            // load tokenizer model
			TokenizerModel model = new TokenizerModel(modelIn);
			Tokenizer tokenizer = new TokenizerME(model);

			// tokenize the sentence
			String[] tokens = tokenizer.tokenize(sentence);
			Span[] spans = tokenizer.tokenizePos(sentence);

			// put tokens into the sentence array
            Sentences[sentenceNr].setSentence(tokens, spans);
            return tokenizer.tokenize(sentence);        // TODO: evtl. auf void umstellen
		}
		catch (Exception e) {
			System.out.println(e);
		}
		return null;
	}

	public static void getParseTree(String sentence, int sentenceNr) throws FileNotFoundException {
	    InputStream modelIn = new FileInputStream("en-parser-chunking.bin");
		try {
            // load parse tree model
			ParserModel model = new ParserModel(modelIn);
			Parser parser = ParserFactory.create(model);

			Parse topParses[] = ParserTool.parseLine(sentence, parser, 1);
			//topParses[0].show();
			System.out.println("Parsing of sentence: " + Sentences[sentenceNr].getText());
			Sentences[sentenceNr].setParseTree(topParses);
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


}
