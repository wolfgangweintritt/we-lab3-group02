package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import at.ac.tuwien.big.we15.lab2.api.*;
import at.ac.tuwien.big.we15.lab2.api.impl.PlayJeopardyFactory;
import at.ac.tuwien.big.we15.lab2.api.impl.SimpleJeopardyGame;
import model.User;
import play.*;
import play.cache.Cache;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.JPA;
import play.db.jpa.Transactional;
import play.mvc.*;
import play.mvc.Security.Authenticated;
import play.i18n.Messages;
import views.html.*;

public class Application extends Controller {
	
	public static Result authenticationChangeLang(String lang) {
        changeLang(lang);
        return showLogin();
    }

    public static Result showLogin() {
    	return ok(authentication.render(Form.form(User.class)));
    }
    
    public static Result showRegistration() {
		return ok(registration.render(Form.form(User.class)));
    }
    
    @Transactional
    public static Result authenticate() {
    	Form<User> authenticationForm = Form.form(User.class).bindFromRequest();
    	User u = getUserFromPersistence(authenticationForm.data().get("name"));
		if (authenticationForm.hasErrors() || u == null) {
			// Wrong Username
			//return badRequest(registration.render(registerForm, Messages.get("registration.error")));
			return badRequest(authentication.render(authenticationForm));
		} else {
			if (u.getPassword().equals(authenticationForm.data().get("password"))) {
				// Store Username in Session & start game
				session("user", authenticationForm.data().get("name"));
				return redirect(routes.Application.showAllQuestions());
			} else {
				// Wrong Password
				return badRequest(authentication.render(authenticationForm));
			}
		}
    }

    @Transactional
    public static Result registration() {
    	EntityManager em = JPA.em();
		Form<User> registrationForm = Form.form(User.class).bindFromRequest();
		if (registrationForm.hasErrors()  || (getUserFromPersistence(registrationForm.data().get("name")) != null )) {
			/*String errorMsg = "";
			java.util.Map<String, List<play.data.validation.ValidationError>> errorsAll = registrationForm.errors();
			for (String field : errorsAll.keySet()) {
				errorMsg += field + " ";
				for (ValidationError error : errorsAll.get(field)) {
					errorMsg += error.message() + ", ";
				}
			}
			System.out.println(errorMsg); */
			//return badRequest(registration.render(registerForm, Messages.get("registration.error")));
			return badRequest(registration.render(registrationForm));
		} else {
			User u = registrationForm.get();
			u.setAvatar(Avatar.getAvatar(u.getAvatarid()));
			em.persist(u);
			return redirect(routes.Application.authenticate());
		}
    }
    
    // called by registration() and authentication(), returns User from Persistence; null if User doesn't exist
    @Transactional
	private static User getUserFromPersistence(String name) {
		EntityManager em = JPA.em();
		return em.find(User.class, name);
	}

	@Transactional
	@Authenticated(Secured.class)
    public static Result showAllQuestions() {

		JeopardyFactory factory = new PlayJeopardyFactory(Messages.get("json.file"));
		EntityManager em = JPA.em();

		JeopardyGame game;
		if(Cache.get(session().get("user") + "game") != null){
			game = (JeopardyGame) Cache.get(session().get("user") + "game");
			/*if(game.getHumanPlayer().getAnsweredQuestions().size() + 
					game.getMarvinPlayer().getAnsweredQuestions().size() >= 10){
				return redirect(routes.Application.showWinner());
			}*/
			DynamicForm dynamicForm = Form.form().bindFromRequest();
			List<Integer> selectedA = dynamicForm.data().keySet().stream().filter
					(s -> s.startsWith("selection")).map
					(s -> Integer.valueOf(dynamicForm.get(s))).collect(Collectors.toList());
			if (selectedA.isEmpty()) {
				return ok(jeopardy.render(game));
			} else { 
				game.answerHumanQuestion(selectedA);
			}

			if(!game.isGameOver()){
				return ok(jeopardy.render(game));
			} else {
				return redirect(routes.Application.showWinner());
			}
		} else {
			// neues Spiel
			// Create Game with Username from Session
			game = new SimpleJeopardyGame(factory, em.find(User.class, session().get("user")));

			// Store in Cache
			Cache.set(session().get("user") + "game", game);
			return ok(jeopardy.render(game));
		}
    }

	@Authenticated(Secured.class)
    public static Result showQuestion() {
    	JeopardyGame game = (JeopardyGame) Cache.get(session().get("user") + "game");

		DynamicForm dynamicForm = Form.form().bindFromRequest();
		if(dynamicForm.get("question_selection") != null) {
			int selected = Integer.valueOf(dynamicForm.get("question_selection"));
			game.chooseHumanQuestion(selected);
		}
		return ok(question.render(game));
    }
    
    @Authenticated(Secured.class)
    public static Result showWinner() {
		JeopardyGame game = (JeopardyGame) Cache.get(session().get("user") + "game");
		Cache.remove(session().get("user") + "game");
		return ok(winner.render(game));
    }

	@Transactional
	@Authenticated(Secured.class)
	public static Result startNewGame(){
		JeopardyFactory factory = new PlayJeopardyFactory(Messages.get("json.file"));
		EntityManager em = JPA.em();

		JeopardyGame game = null;
		Cache.set(session().get("user") + "game", game);

		return redirect(routes.Application.showAllQuestions());
	}

}
