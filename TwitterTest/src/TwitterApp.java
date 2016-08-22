

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterApp {
    static Twitter twitter;

    public static final String [] ep = {"/application/rate_limit_status", "/users/show/:id", "/followers/list", "/statuses/user_timeline"}; // вносим в массив возможные endpoints
    public static int rateLimitStatus;
    public static int showId;
    public static int followersList;
    public static int userTimeline;
    public static String[] keeWords;
    public static int inTime = 0;

    public static void main(String[] args) throws TwitterException, InterruptedException {
        ConfigurationBuilder configuration = new ConfigurationBuilder();
        configuration.setDebugEnabled(true)
                .setOAuthConsumerKey("xxxxxxx")
                .setOAuthConsumerSecret("xxxxxxx")
                .setOAuthAccessToken("128327278-("xxxxxxx")
                .setOAuthAccessTokenSecret("xxxxxxx");

        TwitterFactory tf = new TwitterFactory(configuration.build());
        twitter = tf.getInstance();

        //задаем значения счетчикам лимитов
        createConstuntsValue();

        // Задаем массив твитер-акаунтов
        //String[] usernames = {"poroshenko","Komachka", "arseniy_popov"};


        System.out.println("Введите пользователей, через пробел");
        //Ввод из консоли
        String[] usernames =  scanTheInputData();

        // Задаем масивключевых слов
        //keeWords = new String[]{"готовить", "рисовать"};
        System.out.println("Введите ключевые слова, черз пробел");
        System.out.println("Предпочтительнный ввод латиницей. Возможны проблемы со сканированием cmd кирилицы");
        //Ввод из консоли
        keeWords = scanTheInputData();


        // лист входящих юзеров
        List<User> inputUsers = new ArrayList<>();

        // Заполняем лист юзерами
        for (String name : usernames) {
             // проверка на количевство возможных запросов
            checkLimit("/users/show/:id");
            User user = getUser(name);
            if (user==null){
                System.out.println("Вы ввели не верное имя");
                continue;
            }

            inputUsers.add(user);
        }

        if (inputUsers.size()==0){
            System.out.println("Нет ни одного юзера для проверки");
            System.exit(0);
        }

        for (User inputUser : inputUsers) {

            //System.out.println("Смотрим фоловиров юзера " + inputUser.getName());
            long nextCursor = -1;

            // Просматриваем всех фолловеров аккаунта
            do {

                //System.out.println("Смотрим " + mYcount++ + "заход в цикл подщета фоловеров" + inputUser.getName());

                List<User> listOffolowers = new ArrayList<>();

                checkLimit("/followers/list");
                PagableResponseList<User> usersResponse = twitter.getFollowersList(inputUser.getScreenName(), nextCursor); // followers/list.json лимит 15
                nextCursor = usersResponse.getNextCursor();
                listOffolowers.addAll(usersResponse);

                //System.out.println(listOffolowers.size() + " size of followersList");


                for (User folower : listOffolowers) {

                  //  System.out.println("Заходим в цикл фоловров " + inputUser.getName());
                  //  System.out.println("И смотрим фоловера " + folower.getName());


                    int pageN = 1;
                    List statusrs = new ArrayList<>();

                    while (true){
                        //просматриваем все твиты аккаунта
                        //System.out.println("Смотрим " + pageN + " заход просмотра твитов фоловера " + folower.getName());
                        int size = statusrs.size();
                        Paging page = new Paging(pageN++, 100);

                        checkLimit("/statuses/user_timeline");
                        List thisIterationStatus = null;
                        try {
                            thisIterationStatus = twitter.getUserTimeline(folower.getScreenName(), page); //// statuses/user_timeline.json  180 лимит
                        }
                        catch (TwitterException e) {
                            //если у юзеров защищенныйе аккаунты или удаленные
                            if (e.getStatusCode() == HttpResponseCode.UNAUTHORIZED ||
                                    e.getStatusCode() == HttpResponseCode.NOT_FOUND) {
                                System.out.println("У вас нет доступпа к этому посту");
                            }
                            else {
                                throw e;
                            }
                        }

                        if (thisIterationStatus==null) {
                            continue;
                        }
                        statusrs.addAll(thisIterationStatus);
                        checkforAccord(thisIterationStatus);



                        if (statusrs.size() == size) {
                            break;
                        }
                    }

                }
            }
            while (nextCursor>0);
        }


        System.out.println("Поиск завершен");
    }

    private static User getUser(String name) { // метод проверяет юзеров на действительность

        try {
            User user;
            user = twitter.showUser(name);
            return user;
        } catch (TwitterException e) {
            System.out.println("Произошла ошибка при попытке найти пользователя с таким именем");
        }
        return null;
    }

    private static String[] scanTheInputData() { //метод считывает данные с консоли
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String users;
        try {
                users = br.readLine();
                return users.split(" +");
        } catch (IOException e) {
            System.err.println("Неверный ввод");
        }
        return null;

    }

    private static void checkforAccord(List statusrs) { // метод проверяет твиты в соответствии с патерном из ключевых слов

        if (statusrs.size()!=0) {


            String pattern = getPatern(keeWords);
            Pattern p = Pattern.compile(pattern);
            Matcher m;

            User u = ((Status) statusrs.get(0)).getUser();
            m = p.matcher(u.getDescription());

            if (m.find()) {
                System.out.println("Найденные соответствия:");
                System.out.println(u.getName());
                System.out.println(u.getDescription());
                System.out.println("------------------------------------");

            }


            for (java.lang.Object o : statusrs) {
                m = p.matcher(((Status) o).getText()); //// поиск на соответствие в тексте твита
                if (m.find()) {
                    System.out.println("Найденные соответствия:");
                    System.out.println(((Status) o).getUser().getName());
                    System.out.println(((Status) o).getText());
                    System.out.println("------------------------------------");
                }
            }
        }

    }

    private static void createConstuntsValue() throws TwitterException, InterruptedException { // метод задает значения переменным лимитов запросов
        for (String endpoint : ep) {
            String family = endpoint.split("/",3)[1];
            if (inTime==1) {
                checkLimit("/application/rate_limit_status");
            }
            RateLimitStatus status = twitter.getRateLimitStatus(family).get(endpoint);
            System.out.println(" Endpoint " + endpoint);
            System.out.println("Limit " + status.getLimit());
            System.out.println("Remaining " + status.getRemaining());
            System.out.println();

            if (endpoint.equals(ep[0])) {
                rateLimitStatus = status.getRemaining();
            }
            else if (endpoint.equals(ep[1])){
                showId = status.getRemaining();
            }
            else if (endpoint.equals(ep[2])) {
                followersList = status.getRemaining();
            }
            else {
                userTimeline = status.getRemaining();
            }

        }
        inTime=1;
    }

    private static String getPatern(String[] keeWords) { // метод создает паттерн из ключевых слов
        String pattern = "";
        int size = 0;
        for (String keeWord : keeWords) {
            size++;
            pattern+=keeWord;
            if (size<keeWords.length){
                pattern+="|";
            }
        }
        return pattern;
    }

    public static void checkLimit(String endpoint) throws TwitterException, InterruptedException { // метод проверяет переменные лимитов
         int count = 180;                                                                                //и останавливает программу на 16 минут при привышении лимитов
        if (endpoint.equals(ep[0])) {

            count = --rateLimitStatus;
            }
        else if (endpoint.equals(ep[1])){
            count = --showId;
            }
        else if (endpoint.equals(ep[2])) {
            count =--followersList;
            }
        else {
            count =--userTimeline;
            }


        if (count<=0) {
            System.err.println("Вы превысили лимит запросов. Необходимо подождать");
            long t = System.currentTimeMillis();

            Thread.sleep(960000); // 16 minutes
            long t2 = System.currentTimeMillis();

            System.out.println("Вы ожидали " + (t2 - t)/1000/60  +" минут" );

            createConstuntsValue();

        }
    }



}
