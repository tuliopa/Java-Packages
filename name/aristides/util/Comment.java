package name.aristides.util;

public class Comment {

	private String text;
	private int lineNumber;
	
	
	public Comment(int lineNumber, String text){
		if(lineNumber < 1){
			throw new IllegalArgumentException("Line Number must a positive number");
		}
		
		this.lineNumber = lineNumber;
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
}
