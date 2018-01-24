public class Token {
	private String Word = null;
	private String POSTag = null;
	private String MetaMapAnnotation = null;
	private int Position = -1;
	private int PositionInParseTree = -1;
	private int Length;
	private int LengthInParseTree;

	public Token(){
		return;
	}

	public Token(String word, int pos, int len){
		setWord(word);
		setPosition(pos);
		setPositionInParseTree(pos);
		setLength(len);
		setLengthInParseTree(len);
		return;
	}

	public String getWord() {
		return Word;
	}

	public void setWord(String word) {
		Word = word;
	}

	public String getPOSTag() {
		return POSTag;
	}

	public void setPOSTag(String POSTag) { this.POSTag = POSTag; }

	public int getLength() { return Length; }

	public void setLength(int length) { Length = length; }

	public String getMetaMapAnnotation() {
		return MetaMapAnnotation;
	}

	public void setMetaMapAnnotation(String metaMapAnnotation) {
		MetaMapAnnotation = metaMapAnnotation;
	}

	public int getPosition() {
		return Position;
	}

	public void setPosition(int position) {
		Position = position;
	}

	public int getLengthInParseTree() {
		return LengthInParseTree;
	}

	public int getPositionInParseTree() { return PositionInParseTree; }

	public void setPositionInParseTree(int positionInParseTree) { PositionInParseTree = positionInParseTree; }

	public void setLengthInParseTree(int length) {
		LengthInParseTree = length;
	}
}
