package rnxmpp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.jivesoftware.smack.chat2.Chat;

public class MessagesDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "assac.db";
    private static final int DATABASE_VERSION = 1;
    public static final String EQUALS_STRING = " = ?";

    private SQLiteDatabase writeAbleDB;
    private SQLiteDatabase readAbleDB;

    public MessagesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        writeAbleDB = getWritableDatabase();
        readAbleDB = getReadableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public int getChatIdForContactOfMessage(String contact,String message, String messageDate)
    {
        int retId;
        String[] projection = {
                ChatConsts.ID_COLUMN
        };

        String selection = ChatConsts.CONTACT_COLUMN + EQUALS_STRING;
        String[] selectionArgs = { contact };

        Cursor cursor = readAbleDB.query(
                ChatConsts.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToFirst())
        {
            retId = (int) cursor.getLong(cursor.getColumnIndexOrThrow(ChatConsts.ID_COLUMN));
            cursor.close();
            updateChat(retId,message,messageDate);
            return retId;
        }

        insertChat(contact,message,messageDate);

        return getChatIdForContactOfMessage(contact,message,messageDate);
    }

    public void insertMessage(String id,String text, String createdAt, String user, int recipientId, String image, int chatId)
    {
        ContentValues values = new ContentValues();
        values.put(MessagesConsts.TEXT_COLUMN, text);
        values.put(MessagesConsts.ID_COLUMN, id);
        values.put(MessagesConsts.CREATED_AT_COLUMN, createdAt);
        values.put(MessagesConsts.USER_COLUMN, user);
        values.put(MessagesConsts.RECIPIENT_ID_COLUMN, recipientId);
//        values.put(MessagesConsts.IMAGE_COLUMN, image);
        values.put(MessagesConsts.CHAT_ID_COLUMN, chatId);

        writeAbleDB.insert(MessagesConsts.TABLE_NAME,null, values);
    }

    public void insertChat( String contact, String lastMessage, String LastMessageDate)
    {
        ContentValues values = new ContentValues();
        values.put(ChatConsts.CONTACT_COLUMN, contact);
        values.put(ChatConsts.LAST_MESSAGE_COLUMN, lastMessage);
        values.put(ChatConsts.LAST_MESSAGE_DATE_COLUMN, LastMessageDate);

        writeAbleDB.insert(ChatConsts.TABLE_NAME,null, values);
    }

    public void updateChat(int chatId, String lastMessage, String lastMessageDate)
    {
        ContentValues values = new ContentValues();
        values.put(ChatConsts.LAST_MESSAGE_COLUMN, lastMessage);
        values.put(ChatConsts.LAST_MESSAGE_DATE_COLUMN, lastMessageDate);

        String selection = ChatConsts.ID_COLUMN + EQUALS_STRING;
        String[] selectionArgs = { String.valueOf(chatId) };

        writeAbleDB.update(
                ChatConsts.TABLE_NAME,
                values,
                selection,
                selectionArgs);
    }

    public void closeTransaction()
    {
        writeAbleDB.close();
        readAbleDB.close();
    }

}
