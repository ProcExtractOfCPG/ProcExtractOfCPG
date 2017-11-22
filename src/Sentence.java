import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;


public class Sentence {
	private String Text = null;
	private Token[] Tokens = null;
	private Span TokenSpans[] = null;
	private int SentenceLength = 0;
	private Parse ParseTree[];
	private String EnhancedParseTree;


	public Sentence(String text){ this.Text = text; return; }

	public void setSentence(String[] words, Span[] spans){
		this.SentenceLength = words.length;
		this.Tokens = new Token[SentenceLength];
		//TokenSpans = new Span[SentenceLength];
		this.TokenSpans = spans;
		for (int i = 0; i < words.length; i++) {
			this.Tokens[i] = new Token(words[i], spans[i].getStart(), spans[i].getEnd() - spans[i].getStart());
			//Tokens[i].setWord(words[i]);
		}
		return;
	}

	public void setSentenceLength(int length){ SentenceLength = length;  }

	public Parse[] getParseTree() {
		return ParseTree;
	}

	public void setParseTree(Parse[] parseTree) {
		ParseTree = parseTree;
	}

	public Token[] getTokens() {
		return Tokens;
	}

	public void setTokens(Token[] tokens) {
		Tokens = tokens;
	}

	public Span[] getTokenSpans() {
		return TokenSpans;
	}

	public void setTokenSpans(Span[] tokenSpans) {
		TokenSpans = tokenSpans;
	}

	public String getText() {
		return Text;
	}

	public void setText(String text) {
		Text = text;
	}

	public String getEnhancedParseTree() {
		return EnhancedParseTree;
	}

	public void setEnhancedParseTree(String enhancedParseTree) {
		EnhancedParseTree = enhancedParseTree;
	}
}
