package philosophy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;

import com.florianingerl.util.regex.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Servlet implementation class TestServlet
 * Test links for various functions:
 * https://en.wikipedia.org/wiki/Cat: variety of links, italicized and parenthesized
 * https://en.wikipedia.org/wiki/Wikipedia:Tutorial/Wikipedia_links, italicized links
 * https://en.wikipedia.org/wiki/Referent: parenthesized link in text with multilevel html parent, breaks current check
 * https://en.wikipedia.org/wiki/Linguistics: loop due to redirecting from Lingustic
 */
// A loop is also considered one if a first url redirects to a page visited before under a different url, such as Lingustics/Linguistics
// according to wikipedia article https://en.wikipedia.org/wiki/Wikipedia:Getting_to_Philosophy, there is a loop in mathematics(Mathematics, Quantity, Counting, Element (mathematics), Mathematics), meaning meaning many pages are cut off from philosophy
//TODO: Max depth param?
@WebServlet(urlPatterns = "/FirstServlet", loadOnStartup = 1)
public class TestServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection conn = null; 
	public void init(ServletConfig config) throws ServletException {
		System.out.println("SERVER INIT");
    	try {
    		DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
			conn = DriverManager.getConnection("jdbc:derby:memory:myDB;create=true");
			
			
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
	}
	//https://examples.javacodegeeks.com/enterprise-java/jetty/jetty-servlet-example/
    /**
     * Default constructor. 
     */
    public TestServlet() {
    	
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_SEE_OTHER);
		response.setHeader("Location", "http://localhost:8080/philosophy/TestForm.html");
		response.getWriter().println("Philosophy Servlet - redirecting to form");
		response.sendRedirect("http://localhost:8080/philosophy/TestForm.html");
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//response.getWriter().println("In POST - First Servlet content - Java code geeks");
		String url = request.getParameter("field");
		response.getWriter().println(url);
		//do recursive thing here until philsophy, loop, or dead end
		// navigate to link
		int hops = 0;
		//while loop
		boolean done = false;
		try {
			Statement createstmt = conn.createStatement();
			createstmt.execute("CREATE TABLE links (id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), link VARCHAR(100) NOT NULL,title VARCHAR(100) NOT NULL)");
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		while(!done) {
			// check page title element
			Document doc = Jsoup.connect(url).get();
			String title = doc.getElementById("firstHeading").ownText();
			// if philosophy, done
			if(title.equals("Philosophy")||checkIfVisitedLoop(url,title)) {
				//done
				done = true;
				break;
			}
			try {
				//System.out.println("STORED url"+url);
				PreparedStatement stmt = conn.prepareStatement("INSERT INTO links(link,title) VALUES (?,?)");
				stmt.setString(1, url);
				stmt.setString(2,title);
				stmt.execute();
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			// find first valid link
			//Element maincontent = doc.getElementById("mw-content-text");
			// get wikipedia page content
			//Elements elements = maincontent.select(":root > p");
			Elements p = doc.select("#mw-content-text > .mw-parser-output > p");
			// if no link found either due to dead end page or no content, quit and print hops
			if(p!=null && !p.isEmpty()) {
				// find first valid link
				outerloop:
				for(Element paragraph: p) {
					HashSet<String> parenthesizedlinks = new HashSet<>();
					Pattern parenpattern = Pattern.compile("\\((?:[^)(]+|\\((?:[^)(]+|\\([^)(]*\\))*\\))*\\)");
					Matcher m = parenpattern.matcher(paragraph.html());
					while(m.find()) {
					    Element links = Jsoup.parse(m.group());
					    Elements parenlinks = links.select("a[href]");
					    for(Element plink: parenlinks) {
					    	//abs:href returns nothing
					    	//System.out.println(plink.attr("href"));
					    	parenthesizedlinks.add(plink.attr("href"));
					    	parenthesizedlinks.add("https://en.wikipedia.org"+plink.attr("href"));
					    }
					}
					Elements links = paragraph.select("a[href]");
					for (Element link : links) {
						String cururl = link.attr("abs:href");
						// omit each type of nonlink
						//italicized
						//in order: citations, redlink, must be non-external link, must be wikipedia link, must not be same page, must not be italicized
						boolean italicized = link.parent()!=null ? link.parent().tagName() .equals("i"): false;
						// TODO: Implement db check if visited before, potential check for media. However, wikipedia's media files appear very different from regular links
						
						boolean parenthesized = parenthesizedlinks.contains(cururl);//testIfParenthesized(link.attr("href"), p.html());
						// for testing. may bypass the mathematics loop if checked
						//boolean visited = checkIfVisited(cururl);
						
						if(!cururl.contains("#cite_note")&&!cururl.contains("&redlink=1")&&cururl.startsWith("https://en.wikipedia.org/")&&!cururl.startsWith(url)&&!italicized&&!parenthesized) {
							url = link.attr("abs:href");
							break outerloop;
						}
			        }
				}
				response.getWriter().println(url);
				
			}
			
			hops++;
		}
		response.getWriter().println("Hops: "+hops);
		try {
			Statement dropstmt = conn.createStatement();
			dropstmt.execute("DROP TABLE links");
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
	}
	//If redirect, can only check title of initial page since doc url will not reflect redirect
	private boolean checkIfVisitedLoop(String url,String title) {
		ResultSet rs = null;
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM links WHERE link=? OR title=?");
			stmt.setString(1, url);
			stmt.setString(2, title);
			stmt.execute();
		    rs = stmt.getResultSet();
			 while (rs.next()) {
				if(rs.getString("link").equals(url)|| rs.getString("title").equals(title)) {
					return true;
				}
			 }
		}catch(SQLException e) {
			e.printStackTrace();
		} finally {
			if(rs!=null)
				try {
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
		}
	    return false;
	}
	private boolean checkIfVisited(String url) {
		ResultSet rs = null;
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM links WHERE link=?");
			stmt.setString(1,url);
			stmt.execute();
		    rs = stmt.getResultSet();
			 while (rs.next()) {
				if(rs.getString("link").equals(url)) {
					return true;
				}
			 }
		}catch(SQLException e) {
			e.printStackTrace();
		} finally {
			if(rs!=null)
				try {
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
		}
	    return false;
	}
	private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }
	private static boolean testIfParenthesized(String content,String allcontent) {
		//Pattern parenpattern = Pattern.compile("\\((.*?)\\)");
		//regex recursion may improve depth of parentheses detection. At present, this one goes up to 2 levels: https://stackoverflow.com/questions/546433/regular-expression-to-match-outer-brackets
		// one such pattern can be found here, but it does not function correctly in java: https://stackoverflow.com/questions/546433/regular-expression-to-match-outer-brackets
		// or here in other langs: https://stackoverflow.com/questions/6331065/matching-balanced-parenthesis-in-ruby-using-recursive-regular-expressions-like-p
		Pattern parenpattern = Pattern.compile("\\((?:[^)(]+|\\((?:[^)(]+|\\([^)(]*\\))*\\))*\\)");
		Matcher m = parenpattern.matcher(allcontent);
		while(m.find()) {
		    if(m.group().contains(content)) {
		    	return true;
		    }
		}
		return false;
	}
	private static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }


}

