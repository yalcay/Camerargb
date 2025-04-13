package com.yalcay.camerargb;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import android.app.AlertDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private List<File> photos;
    private final Context context;

    public PhotoAdapter(Context context) {
        this.context = context;
        this.photos = new ArrayList<>();
    }

    public void addPhoto(File photo) {
        photos.add(photo);
        notifyItemInserted(photos.size() - 1);
    }

    public void clearPhotos() {
        photos.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        File photo = photos.get(position);
        Glide.with(context)
            .load(photo)
            .centerCrop()
            .into(holder.imageView);

        holder.imageView.setOnClickListener(v -> showDeleteDialog(position));
    }

    private void showDeleteDialog(int position) {
        new AlertDialog.Builder(context)
            .setTitle("Delete Photo")
            .setMessage("Do you want to delete this photo?")
            .setPositiveButton("Yes", (dialog, which) -> {
                File photoToDelete = photos.get(position);
                if (photoToDelete.delete()) {
                    photos.remove(position);
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, photos.size());
                }
            })
            .setNegativeButton("No", null)
            .show();
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    public List<File> getPhotos() {
        return photos;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        PhotoViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}