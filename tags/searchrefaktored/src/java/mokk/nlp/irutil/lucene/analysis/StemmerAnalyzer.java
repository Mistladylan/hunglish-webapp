package mokk.nlp.irutil.lucene.analysis;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

/**
 * This class is created from Lucene {@link StandardAnalyzer}. It filters
 * {@link StandardTokenizer} with {@link CompoundStemmerTokenFilter},
 * {@link LowerCaseFilter} and {@link StopFilter}, using a list of stop words
 * provided.
 * 
 * 
 * <a name="version"/>
 * <p>
 * You must specify the required {@link Version} compatibility when creating
 * StandardAnalyzer:
 * <ul>
 * <li>As of 2.9, StopFilter preserves position increments
 * <li>As of 2.4, Tokens incorrectly identified as acronyms are corrected (see
 * <a href="https://issues.apache.org/jira/browse/LUCENE-1068">LUCENE-1608</a>
 * </ul>
 */
public class StemmerAnalyzer extends Analyzer {

	/**
	 * assigns a lemmatizer to a fieldName
	 */
	protected Map<String, LemmatizerWrapper> lemmatizers = null;

	private Set<?> stopSet = null;

	/**
	 * Specifies whether deprecated acronyms should be replaced with HOST type.
	 * See {@linkplain https://issues.apache.org/jira/browse/LUCENE-1068}
	 */
	private final boolean replaceInvalidAcronym, enableStopPositionIncrements;

	/**
	 * An unmodifiable set containing some common English words that are usually
	 * not useful for searching.
	 */
	public static final Set<?> STOP_WORDS_SET = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
	private final Version matchVersion;

	/**
	 * Builds an analyzer with the default stop words ({@link #STOP_WORDS_SET}).
	 * 
	 * @param matchVersion
	 *            Lucene version to match See
	 *            {@link <a href="#version">above</a>}
	 */
	public StemmerAnalyzer(Version matchVersion,
			Map<String, LemmatizerWrapper> lemmatizers) {
		this(matchVersion, STOP_WORDS_SET, lemmatizers);
	}

	/**
	 * Builds an analyzer with the given stop words.
	 * 
	 * @param matchVersion
	 *            Lucene version to match See
	 *            {@link <a href="#version">above</a>}
	 * @param stopWords
	 *            stop words
	 */
	public StemmerAnalyzer(Version matchVersion, Set<?> stopWords,
			Map<String, LemmatizerWrapper> lemmatizers) {
		stopSet = stopWords;
		setOverridesTokenStreamMethod(StandardAnalyzer.class);
		enableStopPositionIncrements = StopFilter
				.getEnablePositionIncrementsVersionDefault(matchVersion);
		replaceInvalidAcronym = matchVersion.onOrAfter(Version.LUCENE_24);
		this.matchVersion = matchVersion;
		this.lemmatizers = lemmatizers;

	}

	/**
	 * Builds an analyzer with the stop words from the given file.
	 * 
	 * @see WordlistLoader#getWordSet(File)
	 * @param matchVersion
	 *            Lucene version to match See
	 *            {@link <a href="#version">above</a>}
	 * @param stopwords
	 *            File to read stop words from
	 */
	public StemmerAnalyzer(Version matchVersion, File stopwords,
			Map<String, LemmatizerWrapper> lemmatizers) throws IOException {
		this(matchVersion, WordlistLoader.getWordSet(stopwords), lemmatizers);
	}

	/**
	 * Builds an analyzer with the stop words from the given reader.
	 * 
	 * @see WordlistLoader#getWordSet(Reader)
	 * @param matchVersion
	 *            Lucene version to match See
	 *            {@link <a href="#version">above</a>}
	 * @param stopwords
	 *            Reader to read stop words from
	 */
	public StemmerAnalyzer(Version matchVersion, Reader stopwords,
			Map<String, LemmatizerWrapper> lemmatizers) throws IOException {
		this(matchVersion, WordlistLoader.getWordSet(stopwords), lemmatizers);
	}

	private LemmatizerWrapper getLemmatizer(String fieldName) {
		LemmatizerWrapper lemmatizer = null;
		if (lemmatizers != null) {
			lemmatizer = lemmatizers.get(fieldName);
		}
		return lemmatizer;
	}

	/**
	 * Constructs a {@link StandardTokenizer} filtered by a
	 * {@link StandardFilter}, a {@link LowerCaseFilter} and a
	 * {@link StopFilter}.
	 */
	@Override
	public TokenStream tokenStream(String fieldName, Reader reader) {
		StandardTokenizer tokenStream = new StandardTokenizer(matchVersion,
				reader);
		tokenStream.setMaxTokenLength(maxTokenLength);
		LemmatizerWrapper lemmatizer = getLemmatizer(fieldName);
		TokenStream result = null;
		if (lemmatizer != null) {
			result = new CompoundStemmerTokenFilter(tokenStream, lemmatizer
					.getLemmatizer(), lemmatizer.isReturnOrig(), lemmatizer
					.isReturnOOVOrig(), lemmatizer.isReturnPOS());
		} else {
			result = new StandardFilter(tokenStream);
		}
		result = new LowerCaseFilter(result);
		if (stopSet != null) {
			result = new StopFilter(enableStopPositionIncrements, result,
					stopSet);
		}
		return result;
	}

	private static final class SavedStreams {
		StandardTokenizer tokenStream;
		TokenStream filteredTokenStream;
	}

	/** Default maximum allowed token length */
	public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

	private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

	/**
	 * Set maximum allowed token length. If a token is seen that exceeds this
	 * length then it is discarded. This setting only takes effect the next time
	 * tokenStream or reusableTokenStream is called.
	 */
	public void setMaxTokenLength(int length) {
		maxTokenLength = length;
	}

	/**
	 * @see #setMaxTokenLength
	 */
	public int getMaxTokenLength() {
		return maxTokenLength;
	}

	@Override
	public TokenStream reusableTokenStream(String fieldName, Reader reader)
			throws IOException {
		if (overridesTokenStreamMethod) {
			// LUCENE-1678: force fallback to tokenStream() if we
			// have been subclassed and that subclass overrides
			// tokenStream but not reusableTokenStream
			return tokenStream(fieldName, reader);
		}
		SavedStreams streams = (SavedStreams) getPreviousTokenStream();
		if (streams == null) {
			streams = new SavedStreams();
			setPreviousTokenStream(streams);
			streams.tokenStream = new StandardTokenizer(matchVersion, reader);
			LemmatizerWrapper lemmatizer = getLemmatizer(fieldName);
			if (lemmatizer != null) {
				streams.filteredTokenStream = new CompoundStemmerTokenFilter(
						streams.tokenStream, lemmatizer.getLemmatizer(),
						lemmatizer.isReturnOrig(),
						lemmatizer.isReturnOOVOrig(), lemmatizer.isReturnPOS());
			} else {
				streams.filteredTokenStream = new StandardFilter(
						streams.tokenStream);
			}
			streams.filteredTokenStream = new LowerCaseFilter(
					streams.filteredTokenStream);

			if (stopSet != null) {
				streams.filteredTokenStream = new StopFilter(
						enableStopPositionIncrements,
						streams.filteredTokenStream, stopSet);
			}
		} else {
			streams.tokenStream.reset(reader);
		}
		streams.tokenStream.setMaxTokenLength(maxTokenLength);

		streams.tokenStream.setReplaceInvalidAcronym(replaceInvalidAcronym);

		return streams.filteredTokenStream;
	}
}
