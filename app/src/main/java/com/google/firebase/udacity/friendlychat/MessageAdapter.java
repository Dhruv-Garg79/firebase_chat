package com.google.firebase.udacity.friendlychat;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MyViewHolder>{

    private List<FriendlyMessage> list;

    MessageAdapter(List<FriendlyMessage> list ){
        this.list = list;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_message,viewGroup,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int position) {
        FriendlyMessage message = list.get(position);

        assert message != null;
        if (message.getPhotoUrl() == null){
            myViewHolder.messageText.setText(message.getText());
            myViewHolder.imageView.setVisibility(View.GONE);
            myViewHolder.messageText.setVisibility(View.VISIBLE);
        }
        else {
            myViewHolder.imageView.setVisibility(View.VISIBLE);
            myViewHolder.messageText.setVisibility(View.GONE);
            Glide.with(myViewHolder.imageView.getContext()).load(message.getPhotoUrl()).into(myViewHolder.imageView);
        }
        myViewHolder.userText.setText(message.getName());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class MyViewHolder extends RecyclerView.ViewHolder {

        TextView messageText;
        TextView userText;
        ImageView imageView;

        MyViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageTextView);
            userText = itemView.findViewById(R.id.nameTextView);
            imageView = itemView.findViewById(R.id.photoImageView);
        }
    }
}