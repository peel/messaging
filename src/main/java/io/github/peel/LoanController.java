package io.github.peel;


import com.google.gson.Gson;

import javax.ws.rs.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

@Path("loans")
public class LoanController {
    private static AtomicInteger counter = new AtomicInteger();
    ApplicationService applicationService = new ApplicationService();
    StatusService statusService = new StatusService();
    ApprovalService approvalService = new ApprovalService();

    @POST
    public String apply(String applicationJson){
       Application application = new Gson().fromJson(applicationJson, Application.class);
       ApplicationMessage msg = new ApplicationMessage(application);
       TicketMessage ticketMsg = applicationService.invoke(msg);
       return new Gson().toJson(ticketMsg);
    }

    @PUT
    @Path("{id}")
    public String approve(String approvalJson, @PathParam("id") long id){
        Application application = new Gson().fromJson(approvalJson, Application.class);
        application.setApplicationNo(id);
        ApprovalMessage msg = new ApprovalMessage(application);
        ApplicationMessage applicationMsg = approvalService.invoke(msg);
        return new Gson().toJson(applicationMsg);
    }

    @GET
    @Path("{id}")
    public String status(@PathParam("id") int id){
        ApplicationMessage msg = statusService.invoke(new StatusRequestMessage(id));
        return new Gson().toJson(msg.getApplication());
    }

    public static long getNextId() {
        return counter.incrementAndGet();
    }
}
class Application{
    public Application(){
        applicationNo=LoanController.getNextId();
    }
    public long getApplicationNo() {
        return applicationNo;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public void setApplicationNo(long applicationNo) {
        this.applicationNo = applicationNo;
    }

    private long applicationNo;
    private long amount;
    private String email;
    private String contact;
    private boolean approved;
}
class Ticket {
    private long id;

    public Ticket(long applicationNo) {
        id = applicationNo;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
interface Message{}
class ApplicationMessage implements Message{
    public Application getApplication() {
        return application;
    }

    private final Application application;

    public ApplicationMessage(Application application){
        this.application=application;
    }
}
class StatusRequestMessage implements Message{
    public StatusRequestMessage(long id) {
        this.id=id;
    }

    public long getId() {
        return id;
    }

    private final long id;
}
class TicketMessage implements Message{
    public TicketMessage(Ticket ticket) {
        this.ticket=ticket;
    }

    public Ticket getTicket() {
        return ticket;
    }

    private final Ticket ticket;
}
class ApprovalMessage implements Message{
    public Application getApplication() {
        return application;
    }

    private final Application application;

    ApprovalMessage(Application application) {
        this.application = application;
    }
}
interface Action<T>{
    Message invoke(T input);
}
class CheckRequestValidity implements Action<ApplicationMessage>{
    @Override
    public Message invoke(ApplicationMessage input) {
       return input;
    }
}
class CheckStatus implements Action<StatusRequestMessage>{
    @Override
    public Message invoke(StatusRequestMessage input) {
        return new ApplicationMessage(find(input.getId()));
    }

    private Application find(long id) {
        return Repository.fetch(id);
    }
}
class ApplyForLoan implements Action<ApplicationMessage>{
    @Override
    public Message invoke(ApplicationMessage input) {
        return new TicketMessage(store(input.getApplication()));
    }

    private Ticket store(Application application) {
        return Repository.store(application);
    }
}
class ApproveLoan implements Action<ApplicationMessage>{
    @Override
    public Message invoke(ApplicationMessage input) {
        final String id = getId(input);
        approve(id);
        return new ApplicationMessage(Repository.fetch(id));
    }

    private String getId(ApplicationMessage input) {
        return String.valueOf(input.getApplication().getApplicationNo());
    }

    private Ticket approve(String id) {
        return Repository.approve(id);
    }
}
class Pipeline<T>{
    private List<Action<T>> actions = new ArrayList<Action<T>>();

    public Message execute(T input){
        Message m = null;
        for(Action<T> action : actions){
            m = action.invoke(input);
        }
        return m;
    }

    public Pipeline<T> register(Action<T> action){
        actions.add(action);
        return this;
    }
}
interface Service<T extends Message, U extends Message>{
    public U invoke(T msg);
}
class StatusService implements Service<StatusRequestMessage, ApplicationMessage> {
    private Pipeline pipeline = new Pipeline();

    Pipeline pipeline() {
        return pipeline
                .register(new CheckStatus());
    }

    public ApplicationMessage invoke(StatusRequestMessage msg) {
       return (ApplicationMessage)pipeline().execute(msg);
    }
}
class ApplicationService implements Service<ApplicationMessage, TicketMessage> {
    private Pipeline pipeline = new Pipeline();

    Pipeline pipeline() {
        return pipeline
                .register(new CheckRequestValidity())
                .register(new ApplyForLoan());
    }

    @Override
    public TicketMessage invoke(ApplicationMessage msg) {
        return (TicketMessage)pipeline().execute(msg);
    }
}
class ApprovalService implements Service<ApprovalMessage, ApplicationMessage> {
    private Pipeline pipeline = new Pipeline();

    Pipeline pipeline() {
        return pipeline
                .register(new ApproveLoan());
    }

    @Override
    public ApplicationMessage invoke(ApprovalMessage msg) {
        return (ApplicationMessage) pipeline().execute(msg);
    }
}
class ApplicationException extends RuntimeException {

    public ApplicationException(String message, Exception e) {
        super(message, e);
    }

    private static final long serialVersionUID = 1L;

}
class Repository {

    public final static String FILE_EXTENSION = ".loan";
    public final static String REPOSITORY_ROOT = System.getProperty("user.home") + "/loan";

    public static Application fetch(String ticketId) {
        return fetch(Long.parseLong(ticketId));
    }

    public static Application fetch(long ticketId) {
        File file = fileFromApplication(ticketId);
        try {
            String output = new Scanner(file).useDelimiter("\\Z").next();
            return new Gson().fromJson(output, Application.class);
        } catch (FileNotFoundException e) {
            throw new ApplicationException("Ticket not found", e);
        }
    }

    public static Ticket store(Application application) {
        try {
            new File(REPOSITORY_ROOT).mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(
                    fileFromApplication(application.getApplicationNo()));
            fileOutputStream.write(new Gson().toJson(application).getBytes());
            fileOutputStream.close();
            return new Ticket(application.getApplicationNo());
        } catch (FileNotFoundException e) {
            throw new ApplicationException("Could not store application", e);
        } catch (IOException e) {
            throw new ApplicationException("Could not store application", e);
        }
    }

    private static File fileFromApplication(long applicationNo) {
        return new File(REPOSITORY_ROOT + "/" + applicationNo + FILE_EXTENSION);
    }

    public static Ticket approve(String ticketId) {
        Application application = fetch(ticketId);
        application.setApproved(true);
        store(application);
        return new Ticket(application.getApplicationNo());
    }

}