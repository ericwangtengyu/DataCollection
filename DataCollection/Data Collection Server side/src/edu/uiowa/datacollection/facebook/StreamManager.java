package edu.uiowa.datacollection.facebook;

import java.util.ArrayList;

import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.internal.org.json.JSONArray;
import facebook4j.internal.org.json.JSONException;

/**
 * This Manager handles all Stream downloading operations.
 * 
 * @author Tom
 * 
 */
public class StreamManager
{
	/**
	 * The error code passed by Facebook when the number of requests to the
	 * database pass 600 per 10 minutes, this number is not official and seems
	 * to have been found experimentally online.
	 */
	private static final int RATE_LIMIT_EXCEEDED_ERROR = 613;

	/**
	 * ArrayList holding all StreamObject data
	 */
	private ArrayList<StreamObject> streamObjects = new ArrayList<StreamObject>();

	/**
	 * This creates a new StreamManager, which allows for loading of data from
	 * the FQL stream table.
	 */
	public StreamManager()
	{

	}

	/**
	 * The primary method of this class, this loads stream data.
	 * 
	 * @param session
	 *            The authenticated Facebook session used for FQL requests
	 * @return the loaded StreamObjects
	 * @throws FacebookTokenExpiredError
	 * @throws FacebookUnhandledException
	 */
	public ArrayList<StreamObject> loadStream(Facebook session)
			throws FacebookTokenExpiredError, FacebookUnhandledException
	{
		int offset = 0;

		// Again, 40 is used as the offsetIncrement because larger numbers are
		// unreliable. See Conversation or MessageManager for more details
		int offsetIncrement = 40;

		boolean streamLoaded = false;
		boolean errorOccurred = false;

		while (!streamLoaded)
		{
			errorOccurred = false;

			// FQL query
			String fqlQuery = "SELECT message, description, type, post_id, "
					+ "actor_id, source_id, comment_info, like_info, created_time, "
					+ "updated_time, tagged_ids, message_tags,likes FROM stream "
					+ "WHERE source_id = me() AND created_time > strtotime(\"-1 week\") "
					+ "LIMIT 40 offset " + offset;

			// FQL query to get the posts which are not wrote on the user's
			// wall.
			String fqlQuery1 = "SELECT message, description, type, post_id, "
					+ "source_id, comment_info, like_info, created_time, "
					+ "updated_time, tagged_ids, message_tags, comments, likes FROM stream "
					+ "WHERE actor_id = me() AND filter_key=\"nf\"  AND source_id<>me() AND created_time > strtotime(\"-1 week\") "
					+ "LIMIT 40 offset " + offset;
			try
			{
				// Extract the JSON data
				JSONArray streamList = session.executeFQL(fqlQuery);
				// System.out.println(streamList.toString(1));

				// Extract the JSON data
				JSONArray streamList1 = session.executeFQL(fqlQuery1);
				// System.out.println(streamList1.toString(1));

				// If there is only blank JSON data, we've loaded everything
				if (streamList.length() == 0 && streamList1.length() == 0)
					streamLoaded = true;

				for (int i = 0; i < streamList.length(); i++)
				{
					streamObjects.add(new StreamObject(streamList
							.getJSONObject(i)));
				}

				getCommentsAndLikes(session);
			}
			catch (JSONException e)
			{
				System.out.println("ERROR: JSON improperly formatted.");
				System.out.println(e.getMessage());
			}
			catch (FacebookException e)
			{
				if (e.getErrorCode() == RATE_LIMIT_EXCEEDED_ERROR)
				{
					// We exceeded the rate at which we can access the API
					// so we wait 3 minutes
					try
					{
						System.out
								.println("API Rate limit exceeded, waiting 3 minutes");
						Thread.sleep(1000 * 60 * 3);
					}
					catch (InterruptedException e1)
					{
						e1.printStackTrace();
					}
					errorOccurred = true;
				}
				else if (e.getErrorCode() == FacebookTokenExpiredError.TOKEN_EXPIRED_ERROR)
				{
					throw new FacebookTokenExpiredError(e);
				}
			}

			if (!errorOccurred)
				offset += offsetIncrement;
		}

		return streamObjects;
	}

	/**
	 * This method loads the comment data for all streamObjects
	 * 
	 * @param session
	 *            Current active facebook session
	 * @throws FacebookTokenExpiredError
	 * @throws FacebookUnhandledException
	 */
	public void getCommentsAndLikes(Facebook session)
			throws FacebookTokenExpiredError, FacebookUnhandledException
	{
		for (StreamObject so : streamObjects)
		{
			try
			{
				JSONArray commentArray = session
						.executeFQL("SELECT fromid, text, id, username FROM comment WHERE post_id = \""
								+ so.getPostID() + "\"");
				JSONArray likeArray = session
						.executeFQL("select user_id from like where post_id = \""
								+ so.getPostID() + "\"");

				so.setCommentData(commentArray);
				so.setLikeData(likeArray);
			}
			catch (FacebookException e)
			{
				if (e.getErrorCode() == FacebookTokenExpiredError.TOKEN_EXPIRED_ERROR)
				{
					throw new FacebookTokenExpiredError(e);
				}
				else
				{
					throw new FacebookUnhandledException(e);
				}
			}

		}
	}

	/**
	 * 
	 * @return the list of loaded StreamObjects
	 */
	public ArrayList<StreamObject> getStreamObjects()
	{
		return streamObjects;
	}
}
