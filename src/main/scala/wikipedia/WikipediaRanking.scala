package wikipedia

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import collection.Iterable

import org.apache.spark.rdd.RDD

case class WikipediaArticle(title: String, text: String) {
  /**
    * @return Whether the text of this article mentions `lang` or not
    * @param lang Language to look for (e.g. "Scala")
    */
  def mentionsLanguage(lang: String): Boolean = text.split(' ').contains(lang)
}

object WikipediaRanking extends WikipediaRankingInterface {

  val langs = List(
    "JavaScript", "Java", "PHP", "Python", "C#", "C++", "Ruby", "CSS",
    "Objective-C", "Perl", "Scala", "Haskell", "MATLAB", "Clojure", "Groovy")

  val conf: SparkConf = new SparkConf().setAppName("Wikipedia").setMaster("local[*]")
  val sc: SparkContext = new SparkContext(conf)
  // Hint: use a combination of `sc.parallelize`, `WikipediaData.lines` and `WikipediaData.parse`
  val wikiRdd: RDD[WikipediaArticle] = sc.parallelize(WikipediaData.lines).map(WikipediaData.parse)

  /** Returns the number of articles on which the language `lang` occurs.
   *  Hint1: consider using method `aggregate` on RDD[T].
   *  Hint2: consider using method `mentionsLanguage` on `WikipediaArticle`
   */
  def occurrencesOfLang(lang: String, rdd: RDD[WikipediaArticle]): Int = rdd.aggregate(0)(
    (x,line) => if (line.mentionsLanguage(lang)) x + 1 else x,
    (y,w) => y + w
  )

  def appears(lang: String, rdd : RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])]  = rdd.map{
    case line if line.mentionsLanguage(lang) => (lang,line)
  }.groupByKey()
  // line => if(line.mentionsLanguage(lang)) (lang,line) else ("",line)

  /*
  def where(lang: String, rdd: RDD[WikipediaArticle]): (String, Iterable[WikipediaArticle]) =
    (lang,rdd.filter(article => article.mentionsLanguage(lang)).groupBy(article => article.mentionsLanguage(lang)))
  */
  /* (1) Use `occurrencesOfLang` to compute the ranking of the languages
   *     (`val langs`) by determining the number of Wikipedia articles that
   *     mention each language at least once. Don't forget to sort the
   *     languages by their occurrence, in decreasing order!
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangs(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] = langs.map(
    lang => (lang,occurrencesOfLang(lang,rdd))
  ).sortWith(
    (tuple1,tuple2)=>tuple1._2 > tuple2._2
  )

  /* Compute an inverted index of the set of articles, mapping each language
   * to the Wikipedia pages in which it occurs.
   */

  def makeIndex(langs: List[String], rdd: RDD[WikipediaArticle]): RDD[(String, Iterable[WikipediaArticle])] =
    rdd.flatMap(
    line => langs.filter(line.mentionsLanguage(_)).map(lang => (lang,line))).groupByKey()

    /* First solution I came through, without avoiding cases in which lang was not mentioned
      lang => if(line.mentionsLanguage(lang)) (lang,line) else ("",line))
    ).groupByKey()
*/
  /* (2) Compute the language ranking again, but now using the inverted index. Can you notice
   *     a performance improvement?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsUsingIndex(index: RDD[(String, Iterable[WikipediaArticle])]): List[(String, Int)] = index.mapValues(
    article =>
      article.toList
        .length).collect().toList.sortWith((t1,t2)=> t1._2 > t2._2)

  /* (3) Use `reduceByKey` so that the computation of the index and the ranking are combined.
   *     Can you notice an improvement in performance compared to measuring *both* the computation of the index
   *     and the computation of the ranking? If so, can you think of a reason?
   *
   *   Note: this operation is long-running. It can potentially run for
   *   several seconds.
   */
  def rankLangsReduceByKey(langs: List[String], rdd: RDD[WikipediaArticle]): List[(String, Int)] =
    rdd.flatMap(
    line => langs.filter(line.mentionsLanguage(_)).map(lang => (lang,1))
  ).reduceByKey((a,b) => a+b).collect().toList.sortWith((t1,t2)=> t1._2 > t2._2)

  def main(args: Array[String]): Unit = {

    /* Languages ranked according to (1) */
    val langsRanked: List[(String, Int)] = timed("Part 1: naive ranking", rankLangs(langs, wikiRdd))

    /* An inverted index mapping languages to wikipedia pages on which they appear */
    def index: RDD[(String, Iterable[WikipediaArticle])] = makeIndex(langs, wikiRdd)

    /* Languages ranked according to (2), using the inverted index */
    val langsRanked2: List[(String, Int)] = timed("Part 2: ranking using inverted index", rankLangsUsingIndex(index))

    /* Languages ranked according to (3) */
    val langsRanked3: List[(String, Int)] = timed("Part 3: ranking using reduceByKey", rankLangsReduceByKey(langs, wikiRdd))

    /* Output the speed of each ranking */
    println(timing)
    sc.stop()
  }

  val timing = new StringBuffer
  def timed[T](label: String, code: => T): T = {
    val start = System.currentTimeMillis()
    val result = code
    val stop = System.currentTimeMillis()
    timing.append(s"Processing $label took ${stop - start} ms.\n")
    result
  }
}
