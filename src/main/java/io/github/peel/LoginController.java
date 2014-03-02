package io.github.peel;


import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.List;

@Path("login")
public class LoginController {
    UserService userService = new UserService();

    @POST
    public String login(String login, String pass){
       LoginMessage msg = new LoginMessage(login, pass);
       userService.invoke(msg);
       return String.format("User %s; Pass: %s", login, pass);
    }
}

class User{
    User(String login, String password) {
        this.login = login;
        this.password = password;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    private final String login;
    private final String password;
}

interface Message{

}

class LoginMessage implements Message{
    private final User user;

    private LoginMessage(User user){
        this.user=user;
    }

    public LoginMessage(String login, String password) {
       this(new User(login, password));
    }
}

interface Action<T>{
    Message invoke(T input);
}

class CheckUserSuppliedCredentials implements Action<LoginMessage>{
    @Override
    public Message invoke(LoginMessage input) {
       return input;
    }
}

class Pipeline<T>{
    private List<Action<T>> actions = new ArrayList<Action<T>>();

    public void execute(T input){
        for(Action<T> action : actions){
            action.invoke(input);
        }
    }

    public Pipeline<T> register(Action<T> action){
        actions.add(action);
        return this;
    }
}

class UserService{
    Pipeline loginPipeline =  new Pipeline();

    public UserService(){
        loginPipeline
                    .register(new CheckUserSuppliedCredentials());
    }

    public void invoke(LoginMessage msg) {
        loginPipeline.execute(msg);
    }
}
